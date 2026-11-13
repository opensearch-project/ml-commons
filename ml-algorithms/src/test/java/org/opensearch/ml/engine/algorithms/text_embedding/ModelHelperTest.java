/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.text_embedding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivilegedActionException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.ml.engine.utils.ZipUtils;

public class ModelHelperTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private ModelHelper modelHelper;
    private MLModelFormat modelFormat;
    private String modelId;
    private MLEngine mlEngine;
    private String hashValue = "e13b74006290a9d0f58c1376f9629d4ebc05a0f9385f40db837452b167ae9021";

    @Mock
    ActionListener<Map<String, Object>> actionListener;

    @Mock
    ActionListener<MLRegisterModelInput> registerModelListener;

    Encryptor encryptor;

    @Before
    public void setup() throws URISyntaxException {
        MockitoAnnotations.openMocks(this);
        modelFormat = MLModelFormat.TORCH_SCRIPT;
        modelId = "model_id";
        encryptor = new EncryptorImpl(null, "m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w=");
        mlEngine = new MLEngine(Path.of("/tmp/test" + modelId), encryptor);
        modelHelper = new ModelHelper(mlEngine);
    }

    @Test
    public void testDownloadAndSplit_UrlFailure() {
        modelId = "url_failure_model_id";
        modelHelper
            .downloadAndSplit(modelFormat, modelId, "model_name", "1", "http://testurl", null, FunctionName.TEXT_EMBEDDING, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(PrivilegedActionException.class, argumentCaptor.getValue().getClass());
    }

    @Test
    public void testDownloadAndSplit() throws URISyntaxException {
        String modelUrl = getClass().getResource("traced_small_model.zip").toURI().toString();
        modelHelper
            .downloadAndSplit(modelFormat, modelId, "model_name", "1", modelUrl, hashValue, FunctionName.TEXT_EMBEDDING, actionListener);
        ArgumentCaptor<Map> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertNotNull(argumentCaptor.getValue());
        assertNotEquals(0, argumentCaptor.getValue().size());
    }

    @Test
    public void testDownloadAndSplit_nullHashCode() throws URISyntaxException {
        String modelUrl = getClass().getResource("traced_small_model.zip").toURI().toString();
        modelHelper.downloadAndSplit(modelFormat, modelId, "model_name", "1", modelUrl, null, FunctionName.TEXT_EMBEDDING, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(IllegalArgumentException.class, argumentCaptor.getValue().getClass());
    }

    @Test
    public void testDownloadAndSplit_HashFailure() throws URISyntaxException {
        String modelUrl = getClass().getResource("traced_small_model.zip").toURI().toString();
        modelHelper
            .downloadAndSplit(
                modelFormat,
                modelId,
                "model_name",
                "1",
                modelUrl,
                "wrong_hash_value",
                FunctionName.TEXT_EMBEDDING,
                actionListener
            );
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(IllegalArgumentException.class, argumentCaptor.getValue().getClass());
    }

    @Test
    public void testDownloadAndSplit_Hash() throws URISyntaxException {
        String modelUrl = getClass().getResource("traced_small_model.zip").toURI().toString();
        modelHelper
            .downloadAndSplit(modelFormat, modelId, "model_name", "1", modelUrl, hashValue, FunctionName.TEXT_EMBEDDING, actionListener);
        ArgumentCaptor<Map> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertNotNull(argumentCaptor.getValue());
        assertNotEquals(0, argumentCaptor.getValue().size());
    }

    @Test
    public void testVerifyModelZipFile() throws IOException {
        String modelUrl = getClass().getResource("traced_small_model.zip").toString().substring(5);
        modelHelper.verifyModelZipFile(modelFormat, modelUrl, FunctionName.TEXT_EMBEDDING.toString(), FunctionName.TEXT_EMBEDDING);
    }

    @Test
    public void testVerifyModelZipFile_WrongModelFormat_ONNX() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Model format is TORCH_SCRIPT, but find .onnx file");
        String modelUrl = getClass().getResource("traced_small_model_wrong_onnx.zip").toString().substring(5);
        modelHelper.verifyModelZipFile(modelFormat, modelUrl, FunctionName.TEXT_EMBEDDING.toString(), FunctionName.TEXT_EMBEDDING);
    }

    @Test
    public void testVerifyModelZipFile_WrongModelFormat_TORCH_SCRIPT() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Model format is ONNX, but find .pt file");
        String modelUrl = getClass().getResource("traced_small_model_wrong_onnx.zip").toString().substring(5);
        modelHelper.verifyModelZipFile(MLModelFormat.ONNX, modelUrl, FunctionName.TEXT_EMBEDDING.toString(), FunctionName.TEXT_EMBEDDING);
    }

    @Test
    public void testVerifyModelZipFile_DuplicateModelFile() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Find multiple model files, but expected only one");
        String modelUrl = getClass().getResource("traced_small_model_duplicate_pt.zip").toString().substring(5);
        modelHelper.verifyModelZipFile(modelFormat, modelUrl, FunctionName.TEXT_EMBEDDING.toString(), FunctionName.TEXT_EMBEDDING);
    }

    @Test
    public void testVerifyModelZipFile_MissingTokenizer() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("No tokenizer file");
        String modelUrl = getClass().getResource("traced_small_model_missing_tokenizer.zip").toString().substring(5);
        modelHelper.verifyModelZipFile(modelFormat, modelUrl, FunctionName.TEXT_EMBEDDING.toString(), FunctionName.TEXT_EMBEDDING);
    }

    @Test
    public void testVerifyModelZipFile_ServingPropertiesUnknownClassShapedKey_Rejected() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("references a non-DJL class");
        String zip = buildZip(
            "serving.properties",
            "someFutureFactory=com.attacker.Gadget\n".getBytes(StandardCharsets.UTF_8),
            "model.pt",
            new byte[] { 1 },
            "tokenizer.json",
            "{}"
        );
        modelHelper.verifyModelZipFile(modelFormat, zip, FunctionName.TEXT_EMBEDDING.toString(), FunctionName.TEXT_EMBEDDING);
    }

    @Test
    public void testVerifyModelZipFile_ServingPropertiesUnknownDjlClassShapedKey_Allowed() throws IOException {
        String zip = buildZip(
            "serving.properties",
            "someFutureFactory=ai.djl.foo.Bar\n".getBytes(StandardCharsets.UTF_8),
            "model.pt",
            new byte[] { 1 },
            "tokenizer.json",
            "{}"
        );
        modelHelper.verifyModelZipFile(modelFormat, zip, FunctionName.TEXT_EMBEDDING.toString(), FunctionName.TEXT_EMBEDDING);
    }

    @Test
    public void testVerifyModelZipFile_ServingPropertiesNonClassShapedValue_Allowed() throws IOException {
        String zip = buildZip(
            "serving.properties",
            ("engine=PyTorch\noption.maxLength=256\nmodelVersion=1.2.3\nmodelPath=models/my_model\n").getBytes(StandardCharsets.UTF_8),
            "model.pt",
            new byte[] { 1 },
            "tokenizer.json",
            "{}"
        );
        modelHelper.verifyModelZipFile(modelFormat, zip, FunctionName.TEXT_EMBEDDING.toString(), FunctionName.TEXT_EMBEDDING);
    }

    @Test
    public void testVerifyModelZipFile_ExecutableJar_Rejected() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("disallowed executable file");
        String zip = buildZip("evil.jar", "PK".getBytes(StandardCharsets.UTF_8), "model.pt", new byte[] { 1 }, "tokenizer.json", "{}");
        modelHelper.verifyModelZipFile(modelFormat, zip, FunctionName.TEXT_EMBEDDING.toString(), FunctionName.TEXT_EMBEDDING);
    }

    @Test
    public void testVerifyModelZipFile_ExecutableClass_Rejected() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("disallowed executable file");
        String zip = buildZip("Evil.class", new byte[] { (byte) 0xCA, (byte) 0xFE }, "model.pt", new byte[] { 1 }, "tokenizer.json", "{}");
        modelHelper.verifyModelZipFile(modelFormat, zip, FunctionName.TEXT_EMBEDDING.toString(), FunctionName.TEXT_EMBEDDING);
    }

    @Test
    public void testVerifyModelZipFile_NativeLibrary_Rejected() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("disallowed executable file");
        String zip = buildZip("libx.so", new byte[] { 1 }, "model.pt", new byte[] { 1 }, "tokenizer.json", "{}");
        modelHelper.verifyModelZipFile(modelFormat, zip, FunctionName.TEXT_EMBEDDING.toString(), FunctionName.TEXT_EMBEDDING);
    }

    @Test
    public void testVerifyModelZipFile_ServingPropertiesNonDjlClass_Rejected() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("references a non-DJL class");
        String zip = buildZip(
            "serving.properties",
            "blockFactory=com.attacker.Evil\n".getBytes(StandardCharsets.UTF_8),
            "model.pt",
            new byte[] { 1 },
            "tokenizer.json",
            "{}"
        );
        modelHelper.verifyModelZipFile(modelFormat, zip, FunctionName.TEXT_EMBEDDING.toString(), FunctionName.TEXT_EMBEDDING);
    }

    @Test
    public void testVerifyModelZipFile_ServingPropertiesDjlClass_Allowed() throws IOException {
        String zip = buildZip(
            "serving.properties",
            "translatorFactory=ai.djl.foo.Bar\n".getBytes(StandardCharsets.UTF_8),
            "model.pt",
            new byte[] { 1 },
            "tokenizer.json",
            "{}"
        );
        modelHelper.verifyModelZipFile(modelFormat, zip, FunctionName.TEXT_EMBEDDING.toString(), FunctionName.TEXT_EMBEDDING);
    }

    @Test
    public void testVerifyModelZipFile_ServingPropertiesApplicationPath_Allowed() throws IOException {
        String zip = buildZip(
            "serving.properties",
            "application=nlp/text_embedding\n".getBytes(StandardCharsets.UTF_8),
            "model.pt",
            new byte[] { 1 },
            "tokenizer.json",
            "{}"
        );
        modelHelper.verifyModelZipFile(modelFormat, zip, FunctionName.TEXT_EMBEDDING.toString(), FunctionName.TEXT_EMBEDDING);
    }

    @Test
    public void testVerifyModelDirSafety_ExecutableJar_Rejected() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("disallowed executable file");
        Path dir = tempFolder.newFolder("model_dir_jar").toPath();
        writeTorchScriptArchive(dir.resolve("model.pt"), BENIGN_TORCHSCRIPT_SOURCE);
        Files.write(dir.resolve("evil.jar"), "PK".getBytes(StandardCharsets.UTF_8));
        ModelHelper.verifyModelDirSafety(dir);
    }

    @Test
    public void testVerifyModelDirSafety_ServingPropertiesNonDjlClass_Rejected() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("references a non-DJL class");
        Path dir = tempFolder.newFolder("model_dir_props").toPath();
        writeTorchScriptArchive(dir.resolve("model.pt"), BENIGN_TORCHSCRIPT_SOURCE);
        Files.write(dir.resolve("serving.properties"), "blockFactory=com.attacker.Evil\n".getBytes(StandardCharsets.UTF_8));
        ModelHelper.verifyModelDirSafety(dir);
    }

    @Test
    public void testVerifyModelDirSafety_NestedExecutable_Rejected() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("disallowed executable file");
        Path dir = tempFolder.newFolder("model_dir_nested").toPath();
        writeTorchScriptArchive(dir.resolve("model.pt"), BENIGN_TORCHSCRIPT_SOURCE);
        Path nested = Files.createDirectory(dir.resolve("nested"));
        Files.write(nested.resolve("libx.so"), new byte[] { 1 });
        ModelHelper.verifyModelDirSafety(dir);
    }

    @Test
    public void testVerifyModelDirSafety_CleanDirectory_Allowed() throws IOException {
        Path dir = tempFolder.newFolder("model_dir_clean").toPath();
        writeTorchScriptArchive(dir.resolve("model.pt"), BENIGN_TORCHSCRIPT_SOURCE);
        Files.write(dir.resolve("tokenizer.json"), "{}".getBytes(StandardCharsets.UTF_8));
        Files.write(dir.resolve("serving.properties"), "translatorFactory=ai.djl.foo.Bar\n".getBytes(StandardCharsets.UTF_8));
        ModelHelper.verifyModelDirSafety(dir);
    }

    @Test
    public void testVerifyModelDirSafety_TorchScriptFromFile_Rejected() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("TorchScript model uses disallowed operation: from_file");
        Path dir = tempFolder.newFolder("model_dir_torchscript").toPath();
        writeTorchScriptArchive(dir.resolve("model.pt"), "    _0 = torch.from_file(\"/tmp/secret\", False, 16, dtype=0)\n");
        ModelHelper.verifyModelDirSafety(dir);
    }

    @Test
    public void testVerifyModelDirSafety_DisallowedTorchScriptOpSpellings_Rejected() throws IOException {
        String[] sources = {
            "    _0 = torch.from_file(_1, False, 16, dtype=0)\n",
            "    _0 = ops.aten.from_file(_1, False, 16)\n",
            "    _0 = torch.UntypedStorage.from_file(_1, False, 16)\n",
            "    torch.save(self.secret, \"/tmp/out\")\n",
            "    _0 = ops.debugprims.load_tensor(_1, [16], [1], 0, 0)\n" };
        for (int i = 0; i < sources.length; i++) {
            Path dir = tempFolder.newFolder("model_dir_op_" + i).toPath();
            writeTorchScriptArchive(dir.resolve("model.pt"), sources[i]);
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ModelHelper.verifyModelDirSafety(dir));
            assertTrue(sources[i], e.getMessage().startsWith("TorchScript model uses disallowed operation"));
        }
    }

    @Test
    public void testVerifyModelDirSafety_DisallowedTorchScriptNamespace_Rejected() throws IOException {
        String[] sources = { "    _0 = torch.classes.dist_c10d.FileStore(_1, 1)\n", "    _0 = ops.image.read_file(_1)\n" };
        for (int i = 0; i < sources.length; i++) {
            Path dir = tempFolder.newFolder("model_dir_ns_" + i).toPath();
            writeTorchScriptArchive(dir.resolve("model.pt"), sources[i]);
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ModelHelper.verifyModelDirSafety(dir));
            assertTrue(sources[i], e.getMessage().startsWith("TorchScript model uses disallowed operation namespace"));
        }
    }

    @Test
    public void testVerifyModelDirSafety_AllowedTorchScriptNamespaces_Allowed() throws IOException {
        Path dir = tempFolder.newFolder("model_dir_ns_allowed").toPath();
        writeTorchScriptArchive(
            dir.resolve("model.pt"),
            "    _0 = ops.prim.NumToTensor(_1)\n    _2 = ops.quantized.linear(_0, self._packed_params, 1.0, 0)\n"
                + "    _3 : __torch__.torch.classes.quantized.LinearPackedParamsBase = self._packed_params\n"
        );
        ModelHelper.verifyModelDirSafety(dir);
    }

    @Test
    public void testVerifyModelDirSafety_NonZipModelFile_Rejected() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("is not a valid TorchScript zip archive");
        Path dir = tempFolder.newFolder("model_dir_non_zip").toPath();
        Files.write(dir.resolve("model.pt"), new byte[] { 1 });
        ModelHelper.verifyModelDirSafety(dir);
    }

    @Test
    public void testVerifyModelDirSafety_ZipWithoutTorchScriptCode_Rejected() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("is not a TorchScript archive: no code/ entries");
        Path dir = tempFolder.newFolder("model_dir_no_code").toPath();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(dir.resolve("model.pt").toFile()))) {
            zos.putNextEntry(new ZipEntry("model/data.pkl"));
            zos.write(new byte[] { 1 });
            zos.closeEntry();
        }
        ModelHelper.verifyModelDirSafety(dir);
    }

    @Test
    public void testVerifyModelDirSafety_OversizedTorchScriptSource_Rejected() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("source exceeds the size limit");
        Path dir = tempFolder.newFolder("model_dir_oversized").toPath();
        byte[] block = new byte[1024 * 1024];
        Arrays.fill(block, (byte) ' ');
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(dir.resolve("model.pt").toFile()))) {
            zos.putNextEntry(new ZipEntry("model/code/__torch__.py"));
            for (int i = 0; i < 17; i++) {
                zos.write(block);
            }
            zos.closeEntry();
        }
        ModelHelper.verifyModelDirSafety(dir);
    }

    @Test
    public void testVerifyModelDirSafety_PickleWithDisallowedClassNamespace_Rejected() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("TorchScript model uses disallowed operation namespace: dist_c10d in model/constants.pkl");
        Path dir = tempFolder.newFolder("model_dir_pickle_ns").toPath();
        writeTorchScriptArchive(
            dir.resolve("model.pt"),
            BENIGN_TORCHSCRIPT_SOURCE,
            "model/constants.pkl",
            "c__torch__.torch.classes.dist_c10d\nFileStore\n".getBytes(StandardCharsets.ISO_8859_1)
        );
        ModelHelper.verifyModelDirSafety(dir);
    }

    @Test
    public void testVerifyModelDirSafety_PickleWithQuantizedClass_Allowed() throws IOException {
        // Dynamically quantized models pickle a torch.classes.quantized packed-params object in data.pkl.
        Path dir = tempFolder.newFolder("model_dir_pickle_quantized").toPath();
        writeTorchScriptArchive(
            dir.resolve("model.pt"),
            BENIGN_TORCHSCRIPT_SOURCE,
            "model/data.pkl",
            "c__torch__.torch.classes.quantized\nLinearPackedParamsBase\n".getBytes(StandardCharsets.ISO_8859_1)
        );
        ModelHelper.verifyModelDirSafety(dir);
    }

    @Test
    public void testVerifyModelDirSafety_ExtensionlessFileNamedLikeModelDir_Scanned() throws IOException {
        // DJL loads <dir>/<dir> before <dir>/<dir>.pt, so a malicious extensionless file there must not slip past the scan.
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("TorchScript model uses disallowed operation: from_file");
        Path dir = tempFolder.newFolder("evil").toPath();
        writeTorchScriptArchive(dir.resolve("model.pt"), BENIGN_TORCHSCRIPT_SOURCE);
        writeTorchScriptArchive(dir.resolve("evil"), "    _0 = torch.from_file(_1, False, 16, dtype=0)\n");
        ModelHelper.verifyModelDirSafety(dir);
    }

    @Test
    public void testVerifyModelDirSafety_ExtensionlessNonZipNamedLikeModelDir_Rejected() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("is not a valid TorchScript zip archive");
        Path dir = tempFolder.newFolder("evil_flat").toPath();
        writeTorchScriptArchive(dir.resolve("model.pt"), BENIGN_TORCHSCRIPT_SOURCE);
        Files.write(dir.resolve("EVIL_FLAT"), new byte[] { 1 });
        ModelHelper.verifyModelDirSafety(dir);
    }

    @Test
    public void testVerifyModelDirSafety_NamespaceSplitByCommentOrContinuation_Rejected() throws IOException {
        String[] sources = {
            "    _0 = (torch.ops.  # c\n      image.read_file(_1))\n",
            "    _0 = (torch.ops  # c\n      .image.read_file(_1))\n",
            "    _0 = torch.classes. \\\n      dist_c10d.FileStore(_1, 1)\n" };
        for (int i = 0; i < sources.length; i++) {
            Path dir = tempFolder.newFolder("model_dir_ns_split_" + i).toPath();
            writeTorchScriptArchive(dir.resolve("model.pt"), sources[i]);
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ModelHelper.verifyModelDirSafety(dir));
            assertTrue(sources[i], e.getMessage().startsWith("TorchScript model uses disallowed operation namespace"));
        }
    }

    @Test(timeout = 10_000)
    public void testVerifyModelDirSafety_LongCommentRun_NoCatastrophicBacktracking() throws IOException {
        Path dir = tempFolder.newFolder("model_dir_redos").toPath();
        writeTorchScriptArchive(dir.resolve("model.pt"), "    ops" + "#".repeat(100_000) + "\n");
        ModelHelper.verifyModelDirSafety(dir);
    }

    @Test
    public void testVerifyModelDirSafety_BundledTorchScriptModel_Allowed() throws IOException, URISyntaxException {
        Path dir = tempFolder.newFolder("model_dir_bundled").toPath();
        ZipUtils.unzip(new File(getClass().getResource("all-MiniLM-L6-v2_torchscript_sentence-transformer.zip").toURI()), dir);
        ModelHelper.verifyModelDirSafety(dir);
    }

    private static final String BENIGN_TORCHSCRIPT_SOURCE =
        "class Model(Module):\n  def forward(self, x: Tensor) -> Tensor:\n    return x\n";

    /** Write a minimal TorchScript-style zip with the given source as its only code/ entry. */
    private static void writeTorchScriptArchive(Path path, String source) throws IOException {
        writeTorchScriptArchive(path, source, null, null);
    }

    /** Same as above, plus one extra entry (e.g. a pickle) when extraEntryName is not null. */
    private static void writeTorchScriptArchive(Path path, String source, String extraEntryName, byte[] extraEntryContent)
        throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(path.toFile()))) {
            zos.putNextEntry(new ZipEntry("model/code/__torch__.py"));
            zos.write(source.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            if (extraEntryName != null) {
                zos.putNextEntry(new ZipEntry(extraEntryName));
                zos.write(extraEntryContent);
                zos.closeEntry();
            }
        }
    }

    /**
     * Build a zip file on disk from (name, content) pairs and return its absolute path. Content may be a String or byte[].
     */
    private String buildZip(Object... nameThenContent) throws IOException {
        File zipFile = tempFolder.newFile("model_" + tempFolder.getRoot().list().length + ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            for (int i = 0; i < nameThenContent.length; i += 2) {
                String name = (String) nameThenContent[i];
                Object content = nameThenContent[i + 1];
                byte[] bytes = content instanceof byte[] ? (byte[]) content : ((String) content).getBytes(StandardCharsets.UTF_8);
                zos.putNextEntry(new ZipEntry(name));
                zos.write(bytes);
                zos.closeEntry();
            }
        }
        return zipFile.getAbsolutePath();
    }

    @Test
    public void testDownloadPrebuiltModelConfig_WrongModelName() {
        String taskId = "test_task_id";
        MLRegisterModelInput registerModelInput = MLRegisterModelInput
            .builder()
            .modelName("test_model_name")
            .version("1.0.1")
            .modelGroupId("mockGroupId")
            .modelFormat(modelFormat)
            .deployModel(false)
            .modelNodeIds(new String[] { "node_id1" })
            .build();
        modelHelper.downloadPrebuiltModelConfig(taskId, registerModelInput, registerModelListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(registerModelListener).onFailure(argumentCaptor.capture());
        assertEquals(PrivilegedActionException.class, argumentCaptor.getValue().getClass());
    }

    @Test
    public void testDownloadPrebuiltModelConfig() {
        String taskId = "test_task_id";
        MLRegisterModelInput registerModelInput = MLRegisterModelInput
            .builder()
            .modelName("huggingface/sentence-transformers/all-mpnet-base-v2")
            .version("1.0.1")
            .modelGroupId("mockGroupId")
            .modelFormat(modelFormat)
            .deployModel(false)
            .modelNodeIds(new String[] { "node_id1" })
            .build();
        modelHelper.downloadPrebuiltModelConfig(taskId, registerModelInput, registerModelListener);
        ArgumentCaptor<MLRegisterModelInput> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelInput.class);
        verify(registerModelListener).onResponse(argumentCaptor.capture());
        assertNotNull(argumentCaptor.getValue());
        MLModelConfig modelConfig = argumentCaptor.getValue().getModelConfig();
        assertNotNull(modelConfig);
        assertEquals("mpnet", modelConfig.getModelType());
    }

    @Test
    public void testDownloadPrebuiltModelMetaList() throws PrivilegedActionException {
        String taskId = "test_task_id";
        MLRegisterModelInput registerModelInput = MLRegisterModelInput
            .builder()
            .modelName("huggingface/sentence-transformers/all-mpnet-base-v2")
            .version("1.0.2")
            .modelGroupId("mockGroupId")
            .modelFormat(modelFormat)
            .deployModel(false)
            .modelNodeIds(new String[] { "node_id1" })
            .build();
        List modelMetaList = modelHelper.downloadPrebuiltModelMetaList(taskId, registerModelInput);
        assertEquals("huggingface/sentence-transformers/all-distilroberta-v1", ((Map<String, String>) modelMetaList.get(0)).get("name"));
    }

    @Test
    public void testIsModelAllowed_true() throws PrivilegedActionException {
        String taskId = "test_task_id";
        MLRegisterModelInput registerModelInput = MLRegisterModelInput
            .builder()
            .modelName("huggingface/sentence-transformers/all-mpnet-base-v2")
            .version("1.0.2")
            .modelGroupId("mockGroupId")
            .modelFormat(modelFormat)
            .deployModel(false)
            .modelNodeIds(new String[] { "node_id1" })
            .build();
        List modelMetaList = modelHelper.downloadPrebuiltModelMetaList(taskId, registerModelInput);
        assertTrue(modelHelper.isModelAllowed(registerModelInput, modelMetaList));
    }

    @Test
    public void testIsModelAllowed_WrongModelName() throws PrivilegedActionException {
        String taskId = "test_task_id";
        MLRegisterModelInput registerModelInput = MLRegisterModelInput
            .builder()
            .modelName("huggingface/sentence-transformers/all-mpnet-base-v2-wrong")
            .version("1.0.1")
            .modelGroupId("mockGroupId")
            .modelFormat(modelFormat)
            .deployModel(false)
            .modelNodeIds(new String[] { "node_id1" })
            .build();
        List modelMetaList = modelHelper.downloadPrebuiltModelMetaList(taskId, registerModelInput);
        assertFalse(modelHelper.isModelAllowed(registerModelInput, modelMetaList));
    }

    @Test
    public void testIsModelAllowed_WrongModelVersion() throws PrivilegedActionException {
        String taskId = "test_task_id";
        MLRegisterModelInput registerModelInput = MLRegisterModelInput
            .builder()
            .modelName("huggingface/sentence-transformers/all-mpnet-base-v2")
            .version("000")
            .modelGroupId("mockGroupId")
            .modelFormat(modelFormat)
            .deployModel(false)
            .modelNodeIds(new String[] { "node_id1" })
            .build();
        List modelMetaList = modelHelper.downloadPrebuiltModelMetaList(taskId, registerModelInput);
        assertFalse(modelHelper.isModelAllowed(registerModelInput, modelMetaList));
    }
}
