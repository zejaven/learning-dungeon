# Site Survey — Network Design Process

## The "Reality Check" of Network Design

- In enterprise networking, a logical topology (how data flows) is non-functional if it cannot survive the physical space where it is deployed.
- The site survey is the crucial **"terrain check"** that bridges the gap between software simulation and physical premises.
- Ensures that finalised designs achieve optimal performance, satisfy security baselines, and respect budget constraints.

A professional IT site survey combines physical assessments with the PPDIOO lifecycle to identify gaps, document assets, and ensure long-term operational success.

![Infographic: The IT Site Survey Blueprint — Essential Foundations for Network Success. The left side illustrates the PPDIOO lifecycle methodology (Prepare, Plan and Design, Implement, Operate, Optimise) and its role in lowering Total Cost of Ownership; the right side details critical infrastructure domains — physical environment and cabling, network and connectivity inventory, wireless and security assessment — with a comparison of predictive, passive, and active wireless survey types.](images/slide02-it-site-survey-blueprint.png)

## Context within the PPDIOO Lifecycle Model

- **PPDIOO** = Prepare, Plan, Design, Implement, Operate, and Optimise.
- Site surveys primarily occur within the **Plan and Design phases** of Cisco's PPDIOO model.
- The Plan phase involves the initial information gathering and characterisation of the existing environment.
- The Design phase uses survey data to ensure the network meets business goals, budget, and technological constraints.
- **Prepare, Plan, and Design phases:** establish business goals, gather technical requirements, and develop high-level architecture before implementation.
- **Lowering Total Cost of Ownership (TCO):** early-phase validation improves network availability and ensures infrastructure aligns with specific business requirements.
- **Implement, Operate, and Optimise cycle:** install equipment based on design specifications, monitor performance daily, and proactively resolve issues.

### Core Objectives of a Site Survey

- **Feasibility assessment:** determining if the proposed design is physically possible within the architectural constraints.
- **Resource allocation:** identifying required hardware quantities (APs, switches, cabling).
- **Risk mitigation:** spotting areas of Radio Frequency (RF) interference and architectural barriers before deployment.

## Assessing Physical Premises — Architectural Mechanics

- Engineers must analyse structural physics and spatial limitations that software maps might miss.
- **Blueprint verification:** architectural plans are frequently outdated; physical walkthroughs are required to verify added walls or closed cable pathways.
- **Environmental factors:** identifying unique challenges in industrial environments, such as dust, moisture, or extreme temperatures.

### Understanding RF Attenuation Factors

Attenuation is the loss of signal strength measured in decibels (dB) when RF waves encounter obstacles.

| Material | Impact |
| --- | --- |
| Drywall/Sheetrock | Minimal (~1 dB to 3 dB drop) |
| Brick/Cinderblock | Moderate (~4 dB to 10 dB drop) |
| Reinforced Concrete | Severe (~12 dB to 20+ dB drop), often acting as a complete RF barrier |

### Architectural Distance Limitations (Wired Layer)

- Wired layouts are strictly bound by copper cabling physics.
- **Standard Ethernet (Cat6/Cat6a):** limited to a total structural run length of **100 metres (328 feet)**.
- Surveyors must measure actual pathways — not "as the crow flies" — to determine if additional Intermediate Distribution Frames (IDFs) are required.

## Infrastructure Audit — MDF vs IDF Framework

- **Main Distribution Frame (MDF):** the primary hub where external Internet Service Provider (ISP) lines enter at the Demarcation Point (Demarc).
- **Intermediate Distribution Frame (IDF):** secondary satellite closets housing edge switches for local workstations and Access Points (APs).
- Surveys must ensure IDFs link back to the MDF via high-speed fibre-optic backbone links.

### Power Availability and Integrity Analysis

- **PoE budgets:** modern switches utilise Power over Ethernet (802.3af/at) to power APs and security cameras; 48-port switches can require **370W to 740W**.
- **Clean power:** identification of Uninterruptible Power Supplies (UPS) and backup generators to filter voltage spikes and ensure uptime.
- Verification of dedicated electrical circuits for network closets to prevent overloaded breakers.

### Environmental and HVAC Capacity

- Network hardware generates significant heat and requires a stable climate window of **18°C to 24°C (64°F to 75°F)**.
- Surveys must verify HVAC capabilities and ventilation to prevent hardware throttling or premature failure.
- Considerations for humidity and dust contamination in sensitive electronic environments.

### The Danger of Legacy Infrastructure "Vacuum"

- Rarely are networks built from scratch; existing legacy infrastructure must be audited.
- **Cabling standards:** verification of current cable categories (Cat5e vs Cat6 vs Cat8) and fibre types (multimode vs single-mode).
- Determining if existing racks have adequate open space and efficient rack layout designs for new hardware.

## Wireless Survey Methodologies

| Survey Type | Methodology | Best Use Case |
| --- | --- | --- |
| Predictive | Software-based simulation | Pre-deployment planning using floor plans |
| Passive | Listen-only mode | Monitoring existing performance without network association |
| Active | Measuring signal strength | Validating real-world connectivity and throughput |

### Methodology 1 — Predictive Surveys

- **Theoretical approach:** performed entirely via computer software (e.g. Ekahau AI Pro) using imported digital floor plans.
- **Execution:** trace wall types to assign dB attenuation attributes and place virtual APs.
- **Purpose:** builds a baseline estimate for hardware requirements before the building is accessible or constructed.

### Methodology 2 — Passive Surveys

- **"Listening" mode:** an engineer walks the site with a Wi-Fi analyser to record existing RF metrics without connecting to a network.
- **Key metrics:** gathers signal strength, background noise levels, and identifies rogue Wi-Fi interference.
- Requires specialised hardware; standard laptop wireless cards often lack the ability to capture Signal-to-Noise Ratio (SNR) accurately.

### Methodology 3 — Active Surveys

- **Device simulation:** the measuring device joins the wireless network and moves through the facility like a user device.
- **Benefits:** shows exactly where devices roam between APs and detects APs that broadcast but do not allow associations.
- **Weakness:** roaming behaviour is a function of specific device drivers (e.g. a Windows laptop may not replicate an Android scanner's behaviour).

### Methodology 4 — AP-on-a-Stick (APoS)

- **Empirical proof:** a temporary enterprise AP is mounted on a tripod at operational ceiling height.
- **Execution:** broadcasts a test signal to measure actual throughput, packet loss, and connection drops.
- **Strategic use:** validates performance against unpredictable real-world physical structures and unknown building materials.

### Post-Installation Validation Surveys

- **Final verification:** conducted after the hardware is installed to compare predicted performance against real-world results.
- **Accountability:** ensures the network performs as designed and identifies areas needing minor adjustment for optimal performance.

## Spectrum Analysis and Technical Metrics

### Spectrum Analysis and Interference Sources

- Identification of Electromagnetic Interference (EMI) and Radio Frequency Interference (RFI).
- Common sources: microwaves, fluorescent lighting, elevator motors, and neighbouring wireless networks.
- Mapping the **"noise floor"** — the mixture of all background RF radiation — is essential for ensuring signal clarity.

### Critical Technical Metrics for Success

- **Maximum signal drop:** concrete can drop signals by 20 dB, while drywall drops it by <3 dB.
- **Transmission power:** APs should not use power levels greater than what client devices can handle.
- **Redundancy:** always design for overlap between neighbouring APs to ensure network resilience and smooth roaming.

## The Site Survey Workflow

1. **Needs identification:** defining the purpose of the installation (e.g. high-speed data, video surveillance, IoT); establishing bandwidth requirements and the number of workstations/human resources to be supported.
2. **Documentation gathering:** obtaining digital floor plans and building blueprints; setting the scale of the floor plan in collaborative tools to allow for accurate distance-oriented calculations.
3. **The physical walkthrough:** identifying physical details not visible remotely — equipment racks, modular walls, and varying ceiling heights; noting capture points (e.g. where users enter/exit) and critical coverage areas like executive suites or loading docks.

## The Business Case for Professional Surveys

### Digital Transformation: Moving Beyond the "Yellow Pad"

- Traditional manual methods (paper maps and handwritten notes) are not scalable and prone to error.
- Modern collaborative design software allows for real-time visualisation, instant customer feedback, and automated Bill of Materials (BOM) generation.

### Reducing Unproductive "Truck Rolls"

- Inadequate preparation leads to multiple site visits to gather missed data.
- A single unnecessary "truck roll" can cost hundreds of dollars in labour and fuel, significantly impacting project margins.
- **Solution:** centralised data collection (photos, digital notes) ensures estimators and project managers have a "single source of truth".

### Stakeholder Management and Expectation Setting

- Digital site surveys often take longer than traditional "walkthroughs" but provide far more accurate results.
- Engineers must communicate that the upfront time investment saves hours during the implementation and troubleshooting phases.

### Consequence of Skipping the Site Survey

- **Coverage "dead zones":** discovering blocked signals only after installation.
- **Installation delays:** finding server closets with no power or inadequate cooling after technicians arrive.
- **Budget overruns:** sudden realisation that expensive extra cabling or different hardware is needed due to architectural barriers.

## Safety, Security, and Topology Choices

### Safety and Compliance Considerations

- Ensuring the installation plan abides by industry standards and safety codes.
- Identifying hazardous areas (e.g. near electrical conduits) that are unsafe for cabling hubs.
- Planning for accessibility (elevators, loading docks) to ensure equipment can be safely moved into the building.

### Strategic Topology Choices

- **Daisy chain infrastructure:** high vulnerability and low redundancy; to be avoided.
- **Star/redundant star topology:** minimises network load on switches and provides high redundancy for enterprise environments.

### Integrating Security Surveys

- Surveys for surveillance systems involve defining Operational Requirements: **Detection vs Recognition vs Identification**.
- Ensuring the network can support advanced security features like VLANs and Quality of Service (QoS).

## Final Synthesis — The Bridge to Deployment

- The site survey is the integral precursor to optimisation.
- It ensures the Wired layer (cabling/switches) and the Wireless layer (APs) fit together cleanly inside the infrastructure.

## Key Takeaways

- "Failing to plan is planning to fail": the survey is the foundation of a durable action plan.
- The final output should be a decision-ready proposal backed by empirical physical data.
