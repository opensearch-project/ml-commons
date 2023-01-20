With model serving framework(released in 2.4 as experimental feature), user can upload deep learning NLP model(support 
text embedding model only now) to OpenSearch cluster and run on [ML node](https://opensearch.org/docs/latest/ml-commons-plugin/index/#ml-node).
To get better performance, we need GPU acceleration. We will support GPU ML node from 2.5. This doc explains how to 
prepare GPU ML node to run model serving framework (the setup is one-time effort). This doc focus on two types of GPU 
device: NVIDIA GPU and AWS Inferentia.

# 1. NVIDIA GPU

Test on AWS EC2 `g5.xlarge`, 64-bit(x86)

- Ubuntu AMI:  `Deep Learning AMI GPU PyTorch 1.12.1 (Ubuntu 20.04) 20221114`
- Amazon Linux AMI: `Deep Learning AMI GPU PyTorch 1.12.1 (Amazon Linux 2) 20221114`
- PyTorch: 1.12.1
- CUDA: 11.6

## 1.1 mount nvidia-uvm device

Check if you can see `nvidia-uvm` and `nvidia-uvm-tools` under `/dev` by running
```
ls -al /dev | grep nvidia-uvm
```

If not found, run script `nvidia-uvm-init.sh`. You may need to run with sudo.

Content of `nvidia-uvm-init.sh` (refer to [nvidia doc](https://docs.nvidia.com/cuda/cuda-installation-guide-linux/index.html#runfile-verifications)):

```
#!/bin/bash
## Script to initialize nvidia device nodes.
## https://docs.nvidia.com/cuda/cuda-installation-guide-linux/index.html#runfile-verifications
/sbin/modprobe nvidia
if [ "$?" -eq 0 ]; then
  # Count the number of NVIDIA controllers found.
  NVDEVS=`lspci | grep -i NVIDIA`
  N3D=`echo "$NVDEVS" | grep "3D controller" | wc -l`
  NVGA=`echo "$NVDEVS" | grep "VGA compatible controller" | wc -l`
  N=`expr $N3D + $NVGA - 1`
  for i in `seq 0 $N`; do
    mknod -m 666 /dev/nvidia$i c 195 $i
  done
  mknod -m 666 /dev/nvidiactl c 195 255
else
  exit 1
fi
/sbin/modprobe nvidia-uvm
if [ "$?" -eq 0 ]; then
  # Find out the major device number used by the nvidia-uvm driver
  D=`grep nvidia-uvm /proc/devices | awk '{print $1}'`
  mknod -m 666 /dev/nvidia-uvm c $D 0
  mknod -m 666 /dev/nvidia-uvm-tools c $D 0
else
  exit 1
fi
```

If you can see `nvidia-uvm` and `nvidia-uvm-tools` under `/dev`, then you can start OpenSearch.

# 2. AWS Inferentia

Test on AWS EC2 `inf1.xlarge`,  64-bit(x86)
- Ubuntu AMI:  `Deep Learning AMI GPU PyTorch 1.12.1 (Ubuntu 20.04) 20221114`
- Amazon Linux AMI: `Deep Learning AMI GPU PyTorch 1.12.1 (Amazon Linux 2) 20221114`
- PyTorch: 1.12.1
- CUDA: 11.6

## 2.1 Fresh setup script

You can use these scripts to setup new ML node. You can also check [2.2 Manual way](#22-manual-way) for more details.  

### 2.1.1 Ubuntu 20.04

Test on AWS EC2 `inf1.xlarge`,  64-bit(x86)

Ubuntu AMI:  `Deep Learning AMI GPU PyTorch 1.12.1 (Ubuntu 20.04) 20221114`

Download OpenSearch and set `OS_HOME` first. In this example, we install OpenSearch in home folder.

```
cd ~; wget https://artifacts.opensearch.org/releases/bundle/opensearch/2.5.0/opensearch-2.5.0-linux-x64.tar.gz
tar -xvf opensearch-2.5.0-linux-x64.tar.gz

echo "export OS_HOME=~/opensearch-2.5.0" | tee -a ~/.bash_profile
echo "export PYTORCH_VERSION=1.12.1" | tee -a ~/.bash_profile
source ~/.bash_profile
```

Create shell script file `prepare_torch_neuron.sh` and run it.

Content of `prepare_torch_neuron.sh`:
```
# Configure Linux for Neuron repository updates
. /etc/os-release
sudo tee /etc/apt/sources.list.d/neuron.list > /dev/null <<EOF
deb https://apt.repos.neuron.amazonaws.com ${VERSION_CODENAME} main
EOF
wget -qO - https://apt.repos.neuron.amazonaws.com/GPG-PUB-KEY-AMAZON-AWS-NEURON.PUB | sudo apt-key add -

# Update OS packages
sudo apt-get update -y

################################################################################################################
# To install or update to Neuron versions 1.19.1 and newer from previous releases:
# - DO NOT skip 'aws-neuron-dkms' install or upgrade step, you MUST install or upgrade to latest Neuron driver
################################################################################################################

# Install OS headers
sudo apt-get install linux-headers-$(uname -r) -y

# Install Neuron Driver
sudo apt-get install aws-neuronx-dkms -y

####################################################################################
# Warning: If Linux kernel is updated as a result of OS package update
#          Neuron driver (aws-neuron-dkms) should be re-installed after reboot
####################################################################################

# Install Neuron Tools
sudo apt-get install aws-neuronx-tools -y

######################################################
#   Only for Ubuntu 20 - Install Python3.7
sudo add-apt-repository ppa:deadsnakes/ppa
sudo apt-get install python3.7
######################################################
# Install Python venv and activate Python virtual environment to install    
# Neuron pip packages.
cd ~
sudo apt-get install -y python3.7-venv g++
python3.7 -m venv pytorch_venv
source pytorch_venv/bin/activate
pip install -U pip

# Set Pip repository  to point to the Neuron repository
pip config set global.extra-index-url https://pip.repos.neuron.amazonaws.com

#Install Neuron PyTorch
pip install torch-neuron torchvision
# If need to trace neuron model, install torch neuron with this command
# pip install torch-neuron neuron-cc[tensorflow] "protobuf==3.20.1" torchvision

# If need to trace neuron model, install transfoermers for tracing Huggingface model.
# pip install transformers

# Copy torch neuron lib to OpenSearch
PYTORCH_NEURON_LIB_PATH=~/pytorch_venv/lib/python3.7/site-packages/torch_neuron/lib/
mkdir -p $OS_HOME/lib/torch_neuron; cp -r $PYTORCH_NEURON_LIB_PATH/ $OS_HOME/lib/torch_neuron
export PYTORCH_EXTRA_LIBRARY_PATH=$OS_HOME/lib/torch_neuron/lib/libtorchneuron.so
echo "export PYTORCH_EXTRA_LIBRARY_PATH=$OS_HOME/lib/torch_neuron/lib/libtorchneuron.so" | tee -a ~/.bash_profile

# Increase JVm stack size to >=2MB
echo "-Xss2m" | tee -a $OS_HOME/config/jvm.options
# Increase max file descriptors to 65535
echo "$(whoami) - nofile 65535" | sudo tee -a /etc/security/limits.conf
# max virtual memory areas vm.max_map_count to 262144
sudo sysctl -w vm.max_map_count=262144
```

Exit current terminal or open a new terminal to start OpenSearch.

### 2.1.2 Amazon Linux2

Test on AWS EC2 `inf1.xlarge`,  64-bit(x86)

Amazon Linux AMI: `Deep Learning AMI GPU PyTorch 1.12.1 (Amazon Linux 2) 20221114`

Download OpenSearch and set `OS_HOME` first. In this example, we install OpenSearch in home folder.

```
cd ~; wget https://artifacts.opensearch.org/releases/bundle/opensearch/2.5.0/opensearch-2.5.0-linux-x64.tar.gz
tar -xvf opensearch-2.5.0-linux-x64.tar.gz

echo "export OS_HOME=~/opensearch-2.5.0" | tee -a ~/.bash_profile
echo "export PYTORCH_VERSION=1.12.1" | tee -a ~/.bash_profile
source ~/.bash_profile
```

Create shell script file `prepare_torch_neuron.sh` and run it.

Content of `prepare_torch_neuron.sh`:
```
# Configure Linux for Neuron repository updates
sudo tee /etc/yum.repos.d/neuron.repo > /dev/null <<EOF
[neuron]
name=Neuron YUM Repository
baseurl=https://yum.repos.neuron.amazonaws.com
enabled=1
metadata_expire=0
EOF
sudo rpm --import https://yum.repos.neuron.amazonaws.com/GPG-PUB-KEY-AMAZON-AWS-NEURON.PUB
# Update OS packages
sudo yum update -y
################################################################################################################
# To install or update to Neuron versions 1.19.1 and newer from previous releases:
# - DO NOT skip 'aws-neuron-dkms' install or upgrade step, you MUST install or upgrade to latest Neuron driver
################################################################################################################
# Install OS headers
sudo yum install kernel-devel-$(uname -r) kernel-headers-$(uname -r) -y
# Install Neuron Driver
####################################################################################
# Warning: If Linux kernel is updated as a result of OS package update
#          Neuron driver (aws-neuron-dkms) should be re-installed after reboot
####################################################################################
sudo yum install aws-neuronx-dkms -y
# Install Neuron Tools
sudo yum install aws-neuronx-tools -y

# Install Python venv and activate Python virtual environment to install    
# Neuron pip packages.
cd ~
sudo yum install -y python3.7-venv gcc-c++
python3.7 -m venv pytorch_venv
source pytorch_venv/bin/activate
pip install -U pip

# Set Pip repository  to point to the Neuron repository
pip config set global.extra-index-url https://pip.repos.neuron.amazonaws.com

#Install Neuron PyTorch
pip install torch-neuron torchvision
# If need to trace neuron model, install torch neuron with this command
# pip install torch-neuron neuron-cc[tensorflow] "protobuf<4" torchvision

# If need to trace neuron model, install transfoermers for tracing Huggingface model.
# pip install transformers

# Copy torch neuron lib to OpenSearch
PYTORCH_NEURON_LIB_PATH=~/pytorch_venv/lib/python3.7/site-packages/torch_neuron/lib/
mkdir -p $OS_HOME/lib/torch_neuron; cp -r $PYTORCH_NEURON_LIB_PATH/ $OS_HOME/lib/torch_neuron
export PYTORCH_EXTRA_LIBRARY_PATH=$OS_HOME/lib/torch_neuron/lib/libtorchneuron.so
echo "export PYTORCH_EXTRA_LIBRARY_PATH=$OS_HOME/lib/torch_neuron/lib/libtorchneuron.so" | tee -a ~/.bash_profile
# Increase JVm stack size to >=2MB
echo "-Xss2m" | tee -a $OS_HOME/config/jvm.options
# Increase max file descriptors to 65535
echo "$(whoami) - nofile 65535" | sudo tee -a /etc/security/limits.conf
# max virtual memory areas vm.max_map_count to 262144
sudo sysctl -w vm.max_map_count=262144
```

Exit current terminal or open a new terminal to start OpenSearch.

## 2.2 Manual way
### 2.2.1 Install Driver

Refer to [Deploy on AWS ML accelerator instance](https://awsdocs-neuron.readthedocs-hosted.com/en/latest/frameworks/torch/torch-neuron/setup/pytorch-install.html#deploy-on-aws-ml-accelerator-instance), choose tab “**Ubuntu 18 AMI/Ubuntu 20 AMI**”, if you are using different operation system, choose different tab accordingly.
Copy the content here for easy reference.

```
# Configure Linux for Neuron repository updates
. /etc/os-release
sudo tee /etc/apt/sources.list.d/neuron.list > /dev/null <<EOF
deb https://apt.repos.neuron.amazonaws.com ${VERSION_CODENAME} main
EOF

wget -qO - https://apt.repos.neuron.amazonaws.com/GPG-PUB-KEY-AMAZON-AWS-NEURON.PUB | sudo apt-key add -

# Update OS packages
sudo apt-get update -y

################################################################################################################
# To install or update to Neuron versions 1.19.1 and newer from previous releases:
# - DO NOT skip 'aws-neuron-dkms' install or upgrade step, you MUST install or upgrade to latest Neuron driver
################################################################################################################

# Install OS headers
sudo apt-get install linux-headers-$(uname -r) -y

# Install Neuron Driver
sudo apt-get install aws-neuronx-dkms -y

####################################################################################
# Warning: If Linux kernel is updated as a result of OS package update
#          Neuron driver (aws-neuron-dkms) should be re-installed after reboot
####################################################################################

export PATH=/opt/aws/neuron/bin:$PATH

######################################################
#   Only for Ubuntu 20 - Install Python3.7
#
# sudo add-apt-repository ppa:deadsnakes/ppa
# sudo apt-get install python3.7
#
######################################################
# Install Python venv and activate Python virtual environment to install    
# Neuron pip packages.
sudo apt-get install -y python3.7-venv g++
python3.7 -m venv pytorch_venv
source pytorch_venv/bin/activate
pip install -U pip


# Set Pip repository  to point to the Neuron repository
pip config set global.extra-index-url https://pip.repos.neuron.amazonaws.com

#Install Neuron PyTorch
pip install torch-neuron torchvision

```

Also install neuron tools for monitoring GPU usage

```
# Install Neuron Tools
sudo apt-get install aws-neuronx-tools -y

export PATH=/opt/aws/neuron/bin:$PATH

# Test 
neuron-top
```

### 2.2.2 Trace model (Optional)

Refer to [Compile on compute instance](https://awsdocs-neuron.readthedocs-hosted.com/en/latest/frameworks/torch/torch-neuron/setup/pytorch-install.html#compile-on-compute-instance)
Inferentia only supports neuron traced model now. If you don’t have traced neuron model, you need to use Inferentia neuron SDK to trace Pytorch model first.

```
# Make sure pytorch_venv activated, refer to "Install Driver" step
source pytorch_venv/bin/activate

# Set Pip repository  to point to the Neuron repository
pip config set global.extra-index-url https://pip.repos.neuron.amazonaws.com

#Install Neuron PyTorch
pip install torch-neuron neuron-cc[tensorflow] "protobuf==3.20.1" torchvision
```

**Trace Huggingface pre-trained model example**

Refer to this [huggingface blog](https://awsdocs-neuron.readthedocs-hosted.com/en/latest/src/examples/pytorch/bert_tutorial/tutorial_pretrained_bert.html) to learn how to trace pre-trained huggingface Bert model.

```
pip install transformers
```


Make sure `pytorch_venv` activated first by running `source pytorch_venv/bin/activate`.

Then run `python trace_huggingface_model.py`, it will generate traced neuron model under `./tmp/sentence-transformers/msmarco-distilbert-base-tas-b`

Content of `trace_huggingface_model.py`:
```
import torch
import torch.neuron
from transformers import AutoTokenizer, AutoModel
import pathlib
import os


def trace_neuron_model(model_name, model_max_length, output_path):
    query = "dummy sentence for tracing neuron model"

    # load tokenizer and model
    tokenizer = AutoTokenizer.from_pretrained(model_name)
    model = AutoModel.from_pretrained(model_name, torchscript=True)

    encoded_input = tokenizer(query, max_length=model_max_length, padding="max_length", return_tensors="pt")
    neuron_inputs = tuple(encoded_input.values())

    # compile model with torch.neuron.trace and update config
    model_neuron = torch.neuron.trace(model, neuron_inputs)

    model.config.update({"traced_sequence_length": model_max_length})

    # save tokenizer, neuron model and config for later use
    model_file_path = pathlib.Path(os.path.join(output_path, model_name, model_name.split('/')[-1] + ".pt"))

    model_file_path.parent.mkdir(parents=True, exist_ok=True)
    model_neuron.save(model_file_path)
    tokenizer.save_pretrained(model_file_path.parent)
    model.config.save_pretrained(model_file_path.parent)
    return os.path.join(os.getcwd(), model_file_path)


if __name__ == '__main__':
    model_id = "sentence-transformers/msmarco-distilbert-base-tas-b"
    print(trace_neuron_model(model_id, 512, "tmp"))
    
```

Then you can compress generated files into zip and upload to ml-commons with upload model API.

Note:

1. Compress files directly into one zip without sub-folder, for example: `cd ./tmp/sentence-transformers/msmarco-distilbert-base-tas-b; zip neuron_traced_model.zip *.pt *.json`

### 2.2.3 OpenSearch

Copy torch neuron lib to OpenSearch lib

```
OS_HOME=<OpenSearch installation path>
# For example, if you install OS_HOME in your home folder, it will be 
# OS_HOME=~/opensearch-2.5.0

# Activate pytorch_venv first if you haven't. Refer to "Install Driver" part
source pytorch_venv/bin/activate

 
# Set pytorch neuron lib path. In this example, we create pytorch_venv in home folder, so 
PYTORCH_NEURON_LIB_PATH=~/pytorch_venv/lib/python3.7/site-packages/torch_neuron/lib/


mkdir -p $OS_HOME/lib/torch_neuron; cp -r $PYTORCH_NEURON_LIB_PATH/ $OS_HOME/lib/torch_neuron
export PYTORCH_EXTRA_LIBRARY_PATH=$OS_HOME/lib/torch_neuron/lib/libtorchneuron.so
```

Increase JVM stack size to >=2MB

```
echo "-Xss2m" | sudo tee -a $OS_HOME/config/jvm.options
```

Then you can start OpenSearch and upload/load traced neuron model.

You may see such error when start OpenSearch

```
[1]: max file descriptors [8192] for opensearch process is too low, increase to at least [65535]
[2]: max virtual memory areas vm.max_map_count [65530] is too low, increase to at least [262144]
```

For the first one,  run this command (need to login a new terminal to take effect)

```
echo "$(whoami) - nofile 65535" | sudo tee -a /etc/security/limits.conf
```

For the second one run this

```
sudo sysctl -w vm.max_map_count=262144
```

# 3. DOCKER

Tested on AWS EC2 `g5.xlarge`, 64-bit(x86)

- Amazon Linux AMI: `AWS Deep Learning Base AMI GPU CUDA 11 (Ubuntu 20.04) 20221104`
- PyTorch: 1.12.1
- Docker: 20.10.21
- CUDA: 11.6
- CUDA Driver: 510.47.03
- Docker Image: nvidia/cuda:11.6.2-cudnn8-devel-ubuntu20.04

Some example commands.

Start nivida/cuda docker container:
```
sudo sysctl -w vm.max_map_count=262144
docker run -it --runtime=nvidia --gpus all -p 9200:9200 nvidia/cuda:11.6.2-cudnn8-devel-ubuntu20.04 /bin/bash
```
Start OpenSearch in nivida/cuda docker container:
```
wget https://artifacts.opensearch.org/releases/bundle/opensearch/2.5.0/opensearch-2.5.0-linux-x64.tar.gz
tar -xvf opensearch-2.5.0-linux-x64.tar.gz
cd opensearch-2.5.0
bash opensearch-tar-install.sh
```





