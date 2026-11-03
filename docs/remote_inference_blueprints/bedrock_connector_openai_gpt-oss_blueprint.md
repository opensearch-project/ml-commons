# Bedrock connector blueprint example for OpenAI GPT-OSS models

Amazon Bedrock now offers open-weight models from OpenAI, including GPT-OSS 120B and GPT-OSS 20B.
These models support reasoning, long context (128K), and are suitable for code generation, scientific analysis, and more.

This blueprint demonstrates how to integrate these models with OpenSearch using the ML Commons connector APIs.

## 0. Add connector endpoint to trusted URLs

Note: This step is only necessary for OpenSearch versions prior to 2.11.0.

```json
PUT /_cluster/settings
{
  "persistent": {
    "plugins.ml_commons.trusted_connector_endpoints_regex": [
      "^https://bedrock-runtime\\..*[a-z0-9-]\\.amazonaws\\.com/.*$"
    ]
  }
}
```
## 1. Enable model access and create IAM role
**Note that these models are only available in the `us-west-2` region**

Before creating the connector, you must:

1. **Enable model access** for the OpenAI GPT OSS models on your AWS account:
    * Go to the Amazon Bedrock Model Access page
    * Locate and enable access under the OpenAI provider:
        * `gpt-oss-120b-1:0`
        * `gpt-oss-20b-1:0`
2. **Create an IAM role** with permissions to invoke Bedrock models:

**Trust policy (for self-managed OpenSearch)**:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "es.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    },
    {
      "Effect": "Allow",
      "Principal": {
        "AWS": "arn:aws:iam::<YOUR_ACCOUNT_ID>:user/<YOUR_USERNAME>"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}

```

**Permissions policy**:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "bedrock:InvokeModel"
      ],
      "Resource": [
        "arn:aws:bedrock:us-west-2::foundation-model/openai.gpt-oss-120b-1:0",
        "arn:aws:bedrock:us-west-2::foundation-model/openai.gpt-oss-20b-1:0"
      ]
    }
  ]
}
```
## 2. Create connector

If you are using self-managed OpenSearch, you must supply AWS credentials.

Note:

* These models are available only in `us-west-2`.
* Supported models:

    * `openai.gpt-oss-120b-1:0`
    * `openai.gpt-oss-20b-1:0`
make sure to fill in the appropriate `bedrock_model_id`.

You can extract the needed credentials from your role by running the following
```bash
 aws sts assume-role \      
  --role-arn arn:aws:iam::<accountId>:role/<your iam role name created in step 1.2> \
  --role-session-name create-connector-session
```
```json
POST /_plugins/_ml/connectors/_create
{
  "name": "Amazon Bedrock Connector: GPT-OSS-120B",
  "description": "Connector to OpenAI GPT OSS 120B via Bedrock",
  "version": 1,
  "protocol": "aws_sigv4",
  "credential": {
    "access_key": "<PLEASE ADD YOUR AWS ACCESS KEY HERE>",
    "secret_key": "<PLEASE ADD YOUR AWS SECRET KEY HERE>",
    "session_token": "<PLEASE ADD YOUR AWS SESSION TOKEN HERE>"
  },
  "parameters": {
    "service_name": "bedrock",
    "bedrock_model_id":"<PLEASE ADD YOUR BEDROCK MODEL ID HERE>"
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "url": "https://bedrock-runtime.us-west-2.amazonaws.com/openai/v1/chat/completions",
      "headers": {
        "content-type": "application/json",
        "x-amz-content-sha256": "required"
      },
      "request_body": "{ \"model\": \"${parameters.bedrock_model_id}\", \"messages\": ${parameters.messages}, \"stream\": false }"
    }
  ]
}

```

Sample response:

```json
{
  "connector_id": "s_Ylh5gBesShpyRwvJDs"
}
```

## 3. Register model

```json
POST /_plugins/_ml/models/_register?deploy=true
{
  "name": "openai.gpt-oss-xxb",
  "function_name": "remote",
  "description": "OpenAI GPT OSS 120B via Bedrock",
  "connector_id": "<YOUR CONNECTOR ID HERE>"
}
```

Sample response:

```json
{
  "task_id": "tPYmh5gBesShpyRwVZBW",
  "status": "CREATED",
  "model_id": "tfYmh5gBesShpyRwVZCk"
}
```

## 4. Test model inference

Make sure to substitute the appropriate `model_id` from the register model step 
```json
POST /_plugins/_ml/models/<model_id>/_predict
{
  "parameters": {
    "messages": [
      {
        "role": "user",
        "content": "Explain how bees can fly"
      }
    ]
  }
}
```

Sample response:

```json
{
  "inference_results": [
    {
      "output": [
        {
          "name": "response",
          "dataAsMap": {
            "id": "chatcmpl-24ede40c",
            "choices": [
              {
                "finish_reason": "stop",
                "index": 0.0,
                "message": {
                  "content": "<reasoning>The user: \"explain how bees...\". They want an explanation. So answer explaining aerodynamics, wing beat, etc.\n\nBe thorough and accessible. Possibly mention \"myth that bees shouldn't be able to fly\". Include physics, Reynolds number, wing shape, unsteady aerodynamics, vortex creation. Mention differences from fixed wing aircraft. Also mention bee size, wing load, muscle power. Could discuss the \"afterburner\" style. Provide concise but thorough. Could also mention about other insects, references. So respond.</reasoning>## The Short Answer  \n\nBees *can* fly because their tiny, flexible wings operate in a very different aerodynamic regime than the fixed wings of an airplane. By beating their wings extremely fast (≈ 200 – 250 times per second) and flexing them in a clever, figure‑eight motion, they generate **unsteady, vortex‑driven lift** that far exceeds what simple static‑wing calculations would predict. In short, bees exploit the physics of low‑Reynolds‑number, flapping‑wing flight, turning a “myth” about “impossible” flight into a triumph of bio‑engineering.\n\n---\n\n## 1. Why the “Impossible‑to‑Fly” Myth Appears\n\n| Conventional (airplane) aerodynamics | Bee aerodynamics |\n|---------------------------------------|-------------------|\n| **Fixed, rigid wing** with a constant angle of attack. Lift ≈ ½ ρ V² S Cₗ (steady‑flow). | **Flapping, flexible wing** that constantly changes angle of attack, shape, and motion. Lift is largely produced by **unsteady vortices** rather than steady pressure differences. |\n| **High Reynolds number** (Re ≈ 10⁶ – 10⁷) → flow is largely inertial, turbulence‑dominated. | **Low Reynolds number** (Re ≈ 200 – 500) → viscous forces are significant, flow stays attached longer, and tiny vortices have a big effect. |\n| **Wing loading** (weight per wing area) is low enough for a modest speed (≈ 30 – 100 m s⁻¹) to generate lift. | **Wing loading** of a honeybee ≈ 10 g / 0.007 m² ≈ 1.4 kg m⁻² (higher than many birds) – must create lift at low forward speed (≈ 1–2 m s⁻¹). |\n\nIf you plug a bee’s dimensions into the **steady‑flow lift equation** (the one a pilot’s textbook uses), it *doesn’t* seem to produce enough lift. That calculation ignores the **unsteady mechanisms** that bees exploit. Hence the old “bee shouldn’t be able to fly” joke.\n\n---\n\n## 2. Key Physical Quantities of a Typical Honeybee (*Apis mellifera*)\n\n| Quantity | Typical Value | Remarks |\n|----------|----------------|---------|\n| Body mass (m) | 0.1 g (1 × 10⁻⁴ kg) | |\n| Wing span (b) | ≈ 12 mm (1.2 cm) | Both wings act together; effective wing area S ≈ 0.007 m² |\n| Wing beat frequency (f) | 200 – 250 Hz (≈ 150 ms per stroke) | Very high for an insect |\n| Stroke amplitude (θ) | ≈ 120°–150° (≈ 2 rad) | Creates large vertical component of wing velocity |\n| Linear wing tip speed (Uₜ) | Uₜ ≈ π b f ≈ 2 m s⁻¹ (order‑of‑magnitude) | Comparable to forward flight speed |\n| Reynolds number (Re) | Re = ρ U c/μ ≈ 200 – 500 | Very low, viscous‑dominated regime |\n| Power output (muscle) | ≈ 0.15 W (≈ 150 mW) | Enough to sustain ~ 5 W kg⁻¹ body‑mass power density |\n\n*ρ* = air density (≈ 1.2 kg m⁻³), *c* ≈ wing chord (≈ 3 mm), μ = dynamic viscosity of air (≈ 1.8 × 10⁻⁵ Pa·s).\n\n---\n\n## 3. How Flapping Wings Generate Lift\n\n### 3.1. Unsteady Aerodynamics & Vortex Rings\n\nWhen a wing **accelerates** rapidly at the start of each half‑stroke, the surrounding fluid cannot instantly adjust. This creates a **leading‑edge vortex (LEV)** that remains attached to the wing surface for much of the stroke. The LEV:\n- Increases the effective angle of attack without causing stall.\n- Produces a low‑pressure region over the wing, yielding **extra lift**.\n- Is shed at the end of the stroke, often forming a **vortex ring** that carries momentum downwards (Newton’s third law → the bee is pushed upward).\n\nThe “figure‑8” wing path that bees use (see diagram below) **re‑attaches the LEV** each stroke, essentially “re‑charging” lift continuously.\n\n```\n       ^  Direction of lift (upward)\n       |\n   ___/ \\___   <-- wing path in a vertical plane (simplified)\n  /         \\\n /           \\   (figure‑8 motion)\n```\n\n### 3.2. Added‑Mass & Inertial Forces\n\nAt low Re, the **added mass** of the surrounding air (the mass of air that must be accelerated with the wing) becomes significant. The **inertial reaction** of this added mass contributes a vertical force component each time the wing **reverses direction**, further augmenting lift.\n\n### 3.3. Wing Flexibility & Passive Pitch\n\nBee wings are **thin membranes** supported by veins. During each stroke they **passively pitch** (rotate) due to aerodynamic torques, automatically setting a favorable angle of attack without active muscular control at that instant. This passive mechanism:\n- Reduces the metabolic cost.\n- Allows the wing to stay near the optimal “high‑lift” configuration throughout the stroke.\n\n### 3.4. Clap‑and‑Fling (for some bees)\n\nSmaller bees (e.g., **bumblebees** and **hoverflies**) employ a “**clap‑and‑fling**” maneuver:\n1. **Clap** – the leading edges of the two wings meet at the top of the stroke, squeezing air out and creating a high‑pressure “clap” force.\n2. **Fling** – the wings rapidly separate, drawing fresh air between them and creating a strong vortex that boosts lift.\n\nHoneybees can use a milder version of this, especially during take‑off or rapid acceleration.\n\n---\n\n## 4. Putting Numbers to the Lift\n\nA simplified estimate of lift from a flapping wing can be expressed as:\n\n\\[\nL \\;\\approx\\; C_{L}^{\\text{unsteady}} \\; \\frac{1}{2}\\, \\rho \\, (U_{\\text{eff}})^{2} \\, S\n\\]\n\nwhere:\n\n- \\(U_{\\text{eff}} \\approx f \\, A\\) is the **effective wing speed** (stroke frequency × stroke amplitude).\n- \\(C_{L}^{\\text{unsteady}}\\) is an *unsteady* lift coefficient, often 2–3× larger than the steady‑flow value (≈ 1.0) thanks to vortex augmentation.\n\nUsing typical honeybee numbers:\n\n- \\(f = 230\\text{ Hz}\\)\n- Stroke amplitude \\(A \\approx 6\\text{ mm}\\) (half‑stroke excursion)\n- \\(U_{\\text{eff}} \\approx 230 \\times 0.006 \\approx 1.4\\text{ m s}^{-1}\\)\n- Wing area \\(S \\approx 7 \\times 10^{-3}\\text{ m}^{2}\\)\n- \\(\\rho = 1.2\\text{ kg m}^{-3}\\)\n- \\(C_{L}^{\\text{unsteady}} \\approx 2.5\\)\n\n\\[\nL \\approx 2.5 \\times \\frac12 (1.2) (1.4)^2 (7\\times10^{-3}) \\approx 2.5 \\times 0.6 \\times 1.96 \\times 7\\times10^{-3} \n\\approx 0.016\\text{ N}\n\\]\n\nThe bee’s weight is \\(W = m g = 1\\times10^{-4}\\text{ kg} \\times 9.81\\text{ m s}^{-2} \\approx 0.0010\\text{ N}\\).\n\nThus **the predicted lift is roughly 15× the bee’s weight**, more than enough to support it even when maneuvering or carrying pollen loads. The excess lift is spent on acceleration, turning, and hovering.\n\n---\n\n## 5. Energy & Power Considerations\n\n### 5.1. Mechanical Power Required\n\nThe mechanical power that the bee’s flight muscles must deliver can be approximated by:\n\n\\[\nP_{\\text{mech}} \\approx \\frac{L^{3/2}} {\\sqrt{2 \\rho S}} \n\\]\n\nPlugging the numbers above (with \\(L \\approx 0.016\\,\\text{N}\\), \\(S = 7\\times10^{-3}\\,\\text{m}^2\\)) gives:\n\n\\[\nP_{\\text{mech}} \\approx \\frac{(0.016)^{3/2}}{\\sqrt{2 \\times 1.2 \\times 7\\times10^{-3}}}\n\\approx 0.015 \\text{ W}\n\\]\n\nA honeybee can produce **≈ 0.1 – 0.2 W** of muscular power, so the available power comfortably exceeds the mechanical requirement, leaving a margin for inefficiencies (muscle conversion, internal wing friction, etc.).\n\n### 5.2. Metabolic Cost\n\nBees oxidize nectar‑derived sugars, generating **≈ 20 J per mg of sugar**. With a flight of a few minutes, a bee consumes only a few milligrams of sugar – a tiny fraction of its daily foraging budget. This low cost is why bees can afford long foraging trips and still return to the hive with a full load.\n\n---\n\n## 6. Why Bees Are Smoother Than Fixed‑Wing Planes\n\n| Feature | Fixed‑wing airplane | Bee |\n|---------|--------------------|-----|\n| **Lift source** | Steady pressure difference across rigid airfoil. | Unsteady LEV, added‑mass, clap‑and‑fling, wing flexion. |\n| **Operating Re** | High (10⁶ – 10⁷) – thin boundary layers, turbulent wake. | Low (200 – 500) – viscous dominated, flow stays attached. |\n| **Control** | Ailerons, elevators, thrust. | Wing pitch & stroke plane angle (via thoracic muscles) + body yaw rotation. |\n| **Power-to-weight** | Engines provide many kW per kg. | Flight muscles ~150 W kg⁻¹ (very high for a biological system). |\n| **Speed** | 30‑200 m s⁻¹ cruising. | 1‑5 m s⁻¹ for foraging; up to ≈ 15 m s⁻¹ in a dive. |\n\nBecause they **don’t rely on steady aerodynamics**, bees are not constrained by the same design trade‑offs (large wings, high speed) that aircraft designers face.\n\n---\n\n## 7. Key Take‑aways\n\n1. **The “bee can’t fly” claim** stems from applying *steady‑flow* airplane equations to a *flapping* insect whose wings operate at low Reynolds numbers.\n2. Bees generate lift mainly via **unsteady vortex dynamics** (leading‑edge vortices, vortex rings) and **added‑mass forces** that are *absent* in fixed‑wing flight.\n3. Their **high wing‑beat frequency** (≈ 200 Hz) and **figure‑8 stroke path** constantly re‑energize those vortices, delivering far more lift than a static wing of the same size could.\n4. **Flexibility** and **passive pitch** of the wing membrane allow the bee to stay near the optimal angle of attack without precise muscular control.\n5. **Power output** from the thoracic flight muscles (~0.15 W) comfortably exceeds the mechanical power needed (~0.015 W), making the flight energetically feasible.\n6. The same principles (LEV, clap‑and‑fling) are used by many other insects (e.g., flies, dragonflies) and are now inspiring **micro‑air vehicles (MAVs)** that mimic flapping flight.\n\n---\n\n## 8. Further Reading & Resources\n\n| Source | Why It’s Good |\n|--------|---------------|\n| **“The Physics of Flight: From Insects to Airplanes”** – R. G. Dudley (2002) | Classic review of insect aerodynamics, includes detailed math and experimental data. |\n| **“Flight of the Bumblebee” – T. J. L. Daley, *Science* (2009)** | Demonstrates the unsteady vortex mechanisms with high‑speed video and flow visualization. |\n| **MIT’s “Self‑propelled Flapping Wing Robot” video** | Shows a bio‑inspired robot that replicates the same vortex‑based lift generation. |\n| **“Bio‑inspired Micro Air Vehicles”** – A. Shyy et al., *Annual Review of Fluid Mechanics* (2010) | Discusses how designers translate bee‑flight physics into engineering. |\n| **Open‑source CFD simulations** – e.g., `openFOAM` flapping wing tutorials | For those who want to simulate LEV formation themselves. |\n\n---\n\n### Bottom Line\n\nBees *do* fly, but they do it **the hard way**—by beating their tiny, flexible wings fast enough to create swirling packets of air that push them upward. The “bee can’t fly” myth is a charming reminder that **nature often solves problems with tricks that simple textbook formulas miss**. Understanding those tricks has not only demystified a classic anecdote but also paved the way for the next generation of tiny, efficient flying machines. 🐝✈️",
                  "role": "assistant"
                }
              }
            ],
            "created": 1.754615955E9,
            "model": "openai.gpt-oss-120b-1:0",
            "service_tier": "standard",
            "system_fingerprint": "fp_bc33001493",
            "object": "chat.completion",
            "usage": {
              "completion_tokens": 2902.0,
              "prompt_tokens": 16.0,
              "total_tokens": 2918.0
            }
          }
        }
      ],
      "status_code": 200
    }
  ]
}
```
