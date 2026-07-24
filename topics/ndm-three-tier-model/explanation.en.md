# Three-Tier Model — Architectural Models

The Cisco Three-Layer Hierarchical Model is a systematic framework for designing scalable, reliable campus networks. Developed as an industry standard (largely by Cisco), it decouples functions into three distinct layers to optimise hardware utilisation and isolate failures, and enforces **deterministic traffic patterns**, making network behaviour predictable and manageable.

![Infographic: Mastering the Cisco Three-Layer Hierarchical Model. Depicts the Core, Distribution, and Access layers stacked as a campus network, with each layer's primary focus and common hardware, alongside strategic design goals — two-tier vs. three-tier architecture, redundancy and high availability, and scalability for future growth.](images/slide02-cisco-three-layer-hierarchical-model.png)

## The Pitfalls of Flat Network Design

- **Scalability issues:** broadcast traffic (e.g. ARP) scales linearly with host count, consuming excessive CPU cycles.
- **Failure domains:** a single localised issue, such as a switching loop, can trigger a catastrophic network-wide outage.
- **Management complexity:** lack of logical boundaries makes troubleshooting and security enforcement difficult.

## The Three Layers at a Glance

| Layer | Role | Primary focus | Common hardware |
|---|---|---|---|
| **Core** | The Backbone | High-speed transport | High-end routers and switches |
| **Distribution** | The Smart Layer | Policy-based connectivity | Multilayer switches and routers |
| **Access** | The Edge | Port density and user access | Layer 2 switches and access points |

Strategic design goals: small networks often use a **"collapsed core"** combining distribution and core; larger networks separate the layers. **Redundant links** at the distribution layer prevent total network failure during link issues, and **modular designs** allow adding new user groups without impacting existing performance.

## Layer 1 — The Access Layer (The Edge)

- **Primary purpose:** acts as the **"front door"** and initial entry point for end stations into the infrastructure.
- **Connectivity:** connects PCs, IP phones, wireless access points, and IoT devices.
- **Characteristics:** prioritises high port density and cost-effective Layer 2 switching.

### Access Layer Technical Functions

- **VLAN segmentation:** segments endpoints into granular Virtual LANs (Data, Voice, Guest) to isolate broadcast domains.
- **Power over Ethernet (PoE):** supplies power directly to infrastructure endpoints like VoIP handsets and cameras.
- **Layer 2 services:** implements Spanning Tree (STP) and Quality of Service (QoS) marking.

### Edge Security Enforcement

- **Port Security:** limits MAC addresses allowed on an interface to mitigate flooding attacks.
- **802.1X authentication:** requires devices to authenticate before gaining network access.
- **DHCP Snooping & DAI:** builds binding tables to prevent DHCP spoofing and ARP poisoning.

## Layer 2 — The Distribution Layer (The Policy Engine)

- **Primary purpose:** serves as the **"smart bridge"** and operational boundary between Layer 2 switching and Layer 3 routing.
- **Aggregation:** funnels data from multiple access switches into higher-speed links toward the core.
- **Control plane:** acts as the central point for routing, filtering, and policy enforcement.

### Distribution Layer Technical Functions

- **Inter-VLAN routing:** acts as the **default gateway** for access layer VLANs, terminating broadcast domains.
- **Policy enforcement:** applies Access Control Lists (ACLs) to filter inter-departmental or ingress/egress traffic.
- **Redundancy:** implements First Hop Redundancy Protocols (**HSRP/VRRP**) for high availability.

### Boundary Definition and Summarisation

- **Demarcation:** the boundary where local device connections transition into advanced routing.
- **Route summarisation:** configured on interfaces toward the core to minimise routing table size and improve convergence.
- **Failure isolation:** prevents Spanning Tree loops or broadcast storms in the access layer from impacting the core.

## Layer 3 — The Core Layer (The Backbone)

- **Primary purpose:** the critical transport backbone optimised strictly for **high-speed, reliable data movement**.
- **Design objective:** handle transit traffic between distribution blocks or data centres with minimal latency.
- **Hardware:** ultra-high-throughput modular chassis switches and high-end backbone routers.

### Core Layer Design Constraints

- **Strict policy exclusion:** to maintain **"wire-speed" performance**, the core must not perform CPU-intensive tasks.
- **Banned operations:** packet filtering (ACLs), deep packet inspection, and complex QoS marking are relegated to the distribution layer.
- **Efficiency:** focuses solely on fast transport between regions of the network.

### Core Layer Fault Tolerance

- **Topology:** frequently utilises **Full-Mesh or Partial-Mesh** architectures to ensure no single point of failure.
- **Convergence:** uses advanced routing protocols (e.g. **OSPF, IS-IS**) with fast timers for **sub-second path recovery**.
- **Hardware redundancy:** redundant, hot-swappable power supplies and control planes.

## Layer Comparison

- **Access:** endpoint attachment, edge admission, Layer 2 focus.
- **Distribution:** policy enforcement, broadcast boundary, Layer 3/2 hybrid.
- **Core:** high-speed, low-latency transport, Layer 3 focus, **no policies**.

## Engineering Principles: Modularity

- **Building block design:** networks expand by adding standardised **"Access-Distribution blocks"** to the existing core.
- **Benefits:** allows growth (adding floors or buildings) without structural downtime or core redesigns.
- **Complexity management:** separates network functions into modules, facilitating easier management.

## High Availability Mechanisms

### SSO and NSF

- **Stateful Switchover (SSO):** synchronises critical protocol state information between redundant supervisors in a chassis.
- **Non-Stop Forwarding (NSF):** works with SSO to continue forwarding packets using existing FIB entries during a supervisor failover.
- **Impact:** minimises packet loss to **milliseconds** during hardware or software malfunctions.

### Link Redundancy — EtherChannel

- Bundles multiple physical interfaces into a single logical **"Port-Channel"**.
- **Benefits:** increases aggregate bandwidth, provides link-level redundancy, and **simplifies the Spanning Tree topology**.
- **Protocols:** **LACP** (IEEE standard) or **PAgP** (Cisco proprietary) for dynamic link bundling.

### Stacking Technologies

- **Cisco StackWise:** integrates up to **nine switches** into a single logical unit with a unified control plane.
- **FlexStack:** a "pay-as-you-grow" stacking model for Layer 2 access switches, providing **1:N redundancy**.
- **Advantage:** eliminates the need for Spanning Tree between switches in the stack and provides distributed forwarding.

### Operational Resiliency — ISSU

- **In-Service Software Upgrade (ISSU):** allows a full Cisco IOS upgrade **without taking the device out of service**.
- **Mechanism:** leverages dual supervisors and NSF/SSO to ensure **less than 200 ms** of traffic loss during the upgrade.
- **Goal:** 100% network uptime even during planned maintenance.

### Advanced Resiliency — UDLD and Dampening

- **Unidirectional Link Detection (UDLD):** detects physical connection errors in fibre-optic environments to prevent loops and black holes.
- **IP Event Dampening:** mitigates the impact of **"flapping" interfaces** by temporarily suppressing them until they stabilise.
- **Purpose:** protects the stability of the routing table from poor signalling or loose connections.

## QoS, Addressing, and Multicast in the Hierarchy

- **QoS Trust Boundary:** established at the access layer, where the switch decides whether to accept or remark packet priorities. **Classification & Marking** identifies traffic classes (VoIP, Video, Data) and marks packets (DSCP/CoS); **Ingress Policing** limits bandwidth usage at the edge to prevent one application from saturating the network.
- **Hierarchical addressing:** structured IP addressing enables efficient **route summarisation** at distribution layers.
- **Multicast design:** uses **PIM-SM (Sparse Mode)** and **Rendezvous Points (RP)** within the hierarchy for efficient traffic replication; **IGMP Snooping** at the access layer ensures multicast traffic is only sent to ports that requested it.

## Evolution — The Two-Tier "Collapsed Core"

- **Implementation:** merges the distribution and core layer functions into a single physical device.
- **Use case:** ideal for smaller businesses (**under 200 devices**) to reduce costs while maintaining hierarchical benefits.
- **Limitations:** scaling beyond a single building or high port density often mandates a move back to three tiers.

## Management and Monitoring

- **RMM tools:** Remote Monitoring and Management platforms provide a **"single pane of glass"** visibility across all three tiers.
- **SNMP:** used to pull performance data from core hardware where agents cannot be installed.
- **Proactive alerting:** sets thresholds for critical backbone links to catch glitches before they become outages.

## The Shift to Leaf-Spine (Architecture Context)

- **Traditional hierarchy:** excels at **"North-South" traffic** (user to internet).
- **Leaf-Spine:** optimised for **"East-West" traffic** (server-to-server) dominant in modern data centres and AI/virtualisation workloads.
- **Hybrid use:** Leaf-Spine principles are increasingly applied to campus designs where Wi-Fi 6 and IoT drive East-West traffic.

## Key Takeaways

- The Three-Tier model transforms chaotic infrastructure into a **deterministic, high-performance system**.
- It remains the architectural foundation for large-scale enterprise campus LANs.
- Resiliency and modularity ensure the network is a foundation for the growth of tomorrow.
