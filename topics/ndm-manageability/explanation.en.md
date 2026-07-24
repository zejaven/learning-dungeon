# Manageability — Core Principles of Network Design

Manageability is the foundational goal of modern network design, ensuring an infrastructure is inherently straightforward to **monitor, maintain, and troubleshoot**. By integrating specific protocols and hierarchical frameworks, designers reduce Operational Expenditure (OpEx) and minimise the risk of human error during the network lifecycle.

![Infographic: The Foundations of Network Manageability — A Designer's Guide. Shows the hierarchical design model (Core, Distribution, Access layers), the four pillars of manageability (Visibility, Standardisation, Automation via IaC, Fault Isolation), and a comparison of in-band vs. out-of-band management access methods.](images/slide02-foundations-of-manageability.png)

## Manageability as a Foundational Design Goal

- Successful designs must balance manageability alongside Scalability, Availability, and Security.
- A network is only as functional as the team's ability to keep it running; speed and security are secondary if the system is unmanageable.
- **Beyond basic support:** the shift is from reactive "break-fix" to **proactive observability**, with a focus on technical mechanics, underlying protocols, and architectural trade-offs.

## The Four Pillars of Manageability

Architects must integrate specific protocols and workflows across four operational pillars: **Visibility, Standardisation, Maintenance/Automation, and Fault Isolation**.

### Pillar 1 — Visibility and Monitoring (Telemetry Mechanics)

- The shift is from passive polling to active, continuous data streams.
- **SNMP (Simple Network Management Protocol):** the traditional architecture using Management Information Bases (MIBs) and regular polling intervals.
- **Streaming Telemetry (push vs pull):** utilising gRPC or Netconf over SSH to push structured data models (YANG) to centralised collectors.
- **Benefit:** sub-second granularity eliminates visibility "blind spots" inherent in 5-minute SNMP intervals.

### Pillar 2 — Standardisation and Consistency

- **Preventing "Network Entropy":** avoiding "snowflake configurations" where every device is unique.
- **Enforcing Golden Images:** standardising on validated hardware SKUs and uniform software releases (e.g., Long-Term Support releases) to ensure predictable bug behaviour, and using hardcoded templates for global settings to prevent "configuration drift".

### Pillar 3 — Ease of Maintenance and Automation

- Manageable networks adapt to operational changes (VLAN provisioning, firmware upgrades) with minimal manual overhead.
- **The SDN advantage — decoupling planes:** Software-Defined Networking (SDN) decouples the Control Plane from the Data Plane. Centralised controllers expose Northbound APIs to administrators, translating "intent" to physical devices via Southbound interfaces.
- **Infrastructure as Code (IaC) frameworks:**
  - **Ansible:** an agentless framework using YAML playbooks for declarative configuration via SSH.
  - **Python-based engines:** libraries like Netmiko and NAPALM allow for vendor-agnostic API interactions and configuration audits.

### Pillar 4 — Fault Isolation and Architectural Troubleshooting

- A manageable design prevents localised component failures from escalating into cascading catastrophic outages.
- **Administrative Boundaries** are defined to ensure issues are physically or logically constrained. Modular designs constrain localised failures (like broadcast storms) to single modules.

## Hierarchical Network Design (The Cisco Model)

Devices are organised into three logical layers — Core, Distribution, and Access. **Benefit:** local traffic stays local; only traffic for other networks moves to higher layers, simplifying troubleshooting.

- **The Access Layer (Edge):** the entry point for end-user devices, providing port access, initial filtering/traffic classification, and security. Manageability focus: high port density, Power-over-Ethernet (PoE), and port security.
- **The Distribution Layer (Policy):** aggregates access switches and enforces routing (including routing between VLANs), ACLs, and QoS policies. Critical role: **route summarisation** to reduce the complexity of core routing tables.
- **The Core Layer (Backbone):** responsible for fast, reliable transport of aggregated traffic with minimal latency, designed for 100% uptime and maximum throughput. Design requirement: high-speed switching, redundancy, and zero CPU-intensive packet manipulation.

### Modular Design — Failure Domains

- **Switch Block Deployment:** deploying routers/switches in pairs to create independent "blocks".
- Ensures that the failure of a single device or block does not cause a systemic network collapse.

## The OSI Network Management Model (FCAPS)

A conceptual framework for organising network resources across five functional areas:

| Area | Purpose |
| --- | --- |
| **Fault** | Detecting, isolating, and logging events to minimise Mean Time To Repair (MTTR) |
| **Configuration** | Tracking the effects of hardware/software versions on network operations |
| **Accounting** | Regulating usage statistics to maximise fairness and identify resource-heavy applications |
| **Performance** | Monitoring throughput, utilisation, and response times to ensure health |
| **Security** | Controlling access to assets and monitoring for inappropriate access to sensitive resources |

## Management Access: In-Band vs Out-of-Band (OOB)

- **In-Band:** management traffic shares production paths; low complexity but vulnerable if the data plane fails.
- **Out-of-Band (OOB):** a physically or logically isolated network dedicated to management; high survivability during disasters.

| | In-Band Management | Out-of-Band (OOB) Management |
|---|---|---|
| **Path** | Shares physical production data links | Dedicated, physically isolated network |
| **State dependency** | Fails if production data plane fails | Independent, stays up during data plane failure |
| **Complexity** | Low; simple VLAN tagging | High; requires dedicated cabling and hardware |

## Operational Strategies and Best Practices

- **The 80/20 Rule (Pareto Principle):** 80% of design effort often focuses on 20% of critical requirements. Designers should prioritise the most expensive components (top 20%) to see the most substantial gain.
- **Deterministic routing and failover:** configuring routing metrics (e.g., OSPF costs) so primary and backup paths are completely predictable. Troubleshooting becomes a function of verifying the deterministic state rather than guessing traffic paths.
- **The network design lifecycle:** a continuous cycle — Plan, Design, Implement, Operate, and Optimise. Manageability is central to the "Operate" phase but must be designed in during "Plan".
- **Capacity planning vs over-provisioning:** using telemetry (NetFlow) to accurately estimate future bandwidth rather than blindly buying excess capacity.
- **The flexibility-usability trade-off:** as flexibility increases (e.g., complex SDN features), usability often decreases due to added abstraction layers.

## Design Anti-Patterns to Avoid

- **Single Point of Failure:** core switches with no redundancy.
- **Overly Flat Networks:** minimal segmentation leading to broadcast storms and security risks.
- **Config Drift:** divergent device settings over time due to manual updates.

## Documentation as a Management Tool

- Requirement for **Logical Diagrams, Physical Diagrams, and Bills of Materials (BOM)**.
- A design is only manageable if its current state is well-documented and transparent to the engineering team.

## Key Takeaways

- Manageability is a foundation for OpEx control and long-term viability.
- Hierarchical models simplify complex environments through modularity.
- Modern management relies on streaming telemetry and automation (IaC) over traditional manual CLI polling.
- Success requires balancing the Hierarchy of Needs: **Connectivity → Integrity → Interoperability → Service Delivery → Autonomy**.
