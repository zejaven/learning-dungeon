# Security — Core Principles of Network Design

Success in modern network design has shifted from baseline metrics (bandwidth, latency) to the fundamental axiom: **a network that is not secure is not functional**. This is the imperative of **Security by Design** — native integration of security into architectural blueprints from day one, to avoid the costly, inefficient, and dangerous gaps left by retroactive measures.

![Infographic: The Blueprints of Resilience — Modern Secure Network Architecture. Contrasts reactive (added-later) vs. proactive (designed-in) security, details the technical defence stack (hybrid encryption, next-generation firewalls, confidential computing), and illustrates the Zero Trust framework (biometrics/MFA, least privilege, microsegmentation).](images/slide02-blueprints-of-resilience.png)

## Proactive vs Reactive Paradigms

- **Reactive (Added-On):** security appliances are forced into existing paths, creating bottlenecks (**hairpinning**) and management blind spots. Threats propagate across flat layouts; tracking is fragmented; choke points create processing delays; the result is post-incident cleanups.
- **Proactive (Designed-In) — Security by Design:** protective countermeasures are embedded into architectural blueprints from the initial phase. It delivers automated asset discovery and profiling, microsegmentation that isolates and contains threats, and hardware-accelerated chips for low latency.
  - **Lower lifecycle financial overhead:** designing security upfront is more cost-efficient than emergency hardware overheads and post-incident cleanups.
  - **Optimised traffic flow:** proactive design ensures traffic flows through scalable, optimised checkpoints dictated by the original network blueprint, without severe performance bottlenecks.

## The Obsolescence of the "Castle-and-Moat" Model

- The historical focus on heavy perimeter defense with complete internal trust is entirely obsolete due to insider threats, stolen credentials, and distributed cloud environments.
- The transition is from static perimeter boundaries to dynamic, **identity-based security**.

## Defense in Depth (DiD) Architecture

- A multi-layered strategy stacking controls across **people, processes, and technology**.
- **Redundancy principle:** ensuring the failure of a single control (e.g., a firewall) does not lead to a total system compromise.

## The Zero Trust Framework

- The operational mantra: **"Never Trust, Always Verify"**.
- Three core principles: **Verify explicitly** (identity, location, device health), **Use least privileged access**, and **Assume breach**.

### The Principle of Least Privilege (PoLP)

- Restricting identities (human or machine) to the absolute bare minimum network routes and system permissions required for their function.
- Application: a marketing assistant should have no network path to HR payroll databases.

### Multi-Factor Authentication (MFA)

- Identity verification using at least two distinct proofs: **Knowledge** (password), **Possession** (FIDO2 token/phone), and **Inherence** (biometrics).
- MFA can block over **99.9%** of identity-based account compromise attacks.

### Conditional Access and Contextual Intelligence

- Dynamic enforcement of access based on real-time factors: device health, geolocation, and session risk levels.
- Access decisions are aligned with **User and Entity Behavior Analytics (UEBA)** to identify anomalies.

## Segmentation: Reducing the Incident "Blast Radius"

Large, flat subnets allow malware to propagate across an enterprise in minutes. Segmentation confines breaches to specific logical zones, preserving the operational integrity of the wider network.

- **VLANs (Layer 2):** isolated broadcast domains using IEEE 802.1Q; prevents cross-departmental traffic visibility.
- **VRFs (Layer 3):** Virtual Routing and Forwarding allows multiple routing tables on one device for multi-tenant isolation.
- **Microsegmentation and software-defined security:** moving beyond department-level isolation to the individual workload level — applying firewall rules directly to virtual network interface cards (vNICs) to prevent lateral movement between servers in the same VLAN.
- **Demilitarised Zones (DMZs):** strategic use of DMZs to host public-facing servers, creating a buffer between the untrusted internet and sensitive internal assets.

## The Evolution of Firewall Technology

- **Stateless filters (Layers 3–4):** basic ACL-based checks; vulnerable to spoofing as they lack connection context.
- **Stateful Inspection (SPI):** tracks active connections in a "State Table" to block unsolicited inbound traffic.
- **Next-Generation Firewalls (NGFW) — Layer 7 visibility:** full visibility into software applications (e.g., distinguishing corporate SharePoint use from personal Dropbox exfiltration), integrating intrusion prevention and application-level controls. NGFWs employ Deep Packet Inspection to scan data payloads for malware beyond simple headers.

### Deep Packet Inspection (DPI) Engines

- Looking past the Layer 4 header into the actual data payload.
- Two computational pipelines: **Signature-Based Analysis** (matching known threat strings) and **Protocol Anomaly Detection** (identifying non-standard behaviour).

### Strategic Firewall Placement

- **Perimeter:** defending the network edge.
- **Internal:** segmenting critical systems (databases/financials) from general internal traffic.
- **Distributed architecture:** using host-based and workload-level firewalls for granular control.

## Encryption Across the Data Lifecycle

### Data in Transit

- Foundational premise: assume the physical transmission medium (fibre, ISP hops, wireless) is compromised.
- Goals: preserving **Confidentiality** (against unauthorised reading) and **Integrity** (against unauthorised modification).

### Symmetric vs Asymmetric Cryptosystems

- **Symmetric (AES-256):** computationally efficient for bulk data but faces key distribution challenges.
- **Asymmetric (RSA/ECC):** solves key exchange using public/private pairs but is resource-intensive.

### Hybrid Cryptographic Architecture (TLS Handshake)

- **Optimisation:** using the asymmetric phase for secure key exchange, then switching to the symmetric phase for high-speed bulk transfer.
- **Structural superiority:** combines the security of public-key distribution with the performance of shared-key encryption.

### IPsec vs TLS in Infrastructure Design

- **IPsec (Layer 3):** encrypts all node-to-node traffic; used for Site-to-Site VPNs in "Tunnel Mode" to hide internal IP mapping.
- **TLS (Layers 6–7):** secures specific application sessions (HTTPS, SMTPS, LDAPS).

### Confidential Computing — Data in Use

- The "third stage" of encryption: protecting data while it is actively being processed in the CPU and memory.
- Hardware-based **Trusted Execution Environments (TEEs)** isolate sensitive workloads from the host OS or hypervisor.
- Adoption: around **75%** of organisations are now piloting or using hardware-based environments to secure data-in-use.

## Role-Based Access Control (RBAC)

- Permissions are grouped into **roles** (Accountant, Admin, Developer) rather than assigned to individual users — simplifying administration and enhancing security through centralised management.
- **Hierarchical RBAC:** senior roles inherit permissions from junior roles (e.g., Manager inherits Employee permissions).
- **Constrained RBAC:** implements **Separation of Duties (SoD)** to prevent fraud.

### Static vs Dynamic Separation of Duties

- **Static (SSD):** prevents a user from being assigned mutually exclusive roles (e.g., Requester and Approver).
- **Dynamic (DSD):** allows a user to hold two roles but prevents their simultaneous activation in one session.

## Monitoring and Detection

### Strategic Sensor Placement for IDS/IPS

- Placement at **natural bottlenecks**: internet entry points, WAN links, and internal departmental boundaries.
- **Inline mode** for active prevention (blocking traffic) vs **Passive/TAP mode** for non-intrusive forensic analysis.

### Logging and SIEM Integration

- Aggregating logs from firewalls, IDS/IPS, and endpoints for real-time analysis.
- Goal: reducing **Dwell Time** (the period an attacker spends undetected inside the network) through automated response tools.

### Securing the Monitoring Infrastructure

- Implementation of an isolated **Management Network** for security tools.
- Monitoring systems must be behind their own firewalls and must not participate in general user authentication domains.

## Network Resiliency and High Availability

- Distinguishing between **Network Resiliency** (link failure), **Device Resiliency** (hardware crash), and **Operational Resiliency** (maintenance).
- Utilising **NSF (Non-Stop Forwarding)** and **SSO (Stateful Switchover)** to maintain data flows during a supervisor failover.

## Security in the Age of Generative AI

- Risks of **data leakage** via public AI tools and **prompt injection** attacks.
- Necessity for **granular access policies for AI**: controlling not just who uses the AI, but what data the AI can access on their behalf.

## Key Takeaways: Security as a Living Property

- Successful design requires **continuous adaptation**, not a one-time setup.
- The ultimate goal: a **traceable architecture** where every configuration connects back to a core business risk requirement.
