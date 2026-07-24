# Requirement Analysis — Network Design Process

Requirement analysis is the process of identifying, gathering, and translating high-level business goals into precise, actionable technical specifications. It is considered the **most critical phase** of the network design process; a flawless architecture that fails to align with business needs is a failed design.

Successful network design prioritises business requirements over hardware selection, ensuring infrastructure is a strategic business enabler.

![Infographic: The Business-Driven Network — A Top-Down Design Blueprint. Phase 1 (Requirement Analysis) covers starting with business drivers, planning for density and growth (2.5 to 3 devices per concurrent user over a 3-to-5-year horizon), and profiling application traffic; a table contrasts top-down vs. bottom-up design approaches; Phase 2 covers core design principles — modularity and hierarchy, fault isolation, and a holistic approach.](images/slide02-business-driven-network-blueprint.png)

## The Top-Down Methodology

- Focuses on the upper layers of the OSI model (Applications, Sessions, Data Transport) before selecting lower-layer hardware like routers and switches.
- Contrast with "Bottom-Up" (connect-the-dots) design, which often leads to performance bottlenecks or expensive **"gold-plating"**.

| | Top-Down Design Approach | Bottom-Up Design Approach |
| --- | --- | --- |
| **Primary Focus** | Business goals and application requirements | Hardware selection and technology models |
| **Risk of Failure** | Low: aligns technology with business purpose | High: often fails to meet business needs |

### Iterative Nature of Analysis

- Requirement analysis is not a one-time event but an **iterative cycle**.
- Initial high-level views are gathered first, followed by **"spiralling downward"** into detailed protocol behaviour and technical specs as the design matures.

## Business Goals, Structure, and Success Criteria

### Identifying Core Business Goals

- Primary objectives often include increasing revenue, reducing operational costs, or expanding into new markets.
- Designers must ask: "Why is the customer embarking on this project?" and "How will the network ensure business success?"

### Understanding Corporate Structure

- Final internetwork designs usually reflect corporate structure (departments, remote offices, vendors).
- Gaining insight into management hierarchy identifies the key decision-makers authorised to accept or reject the proposal.

### Industry-Specific Requirements

- Nature of the business dictates default requirements (e.g. banking necessitates high security for all external links).
- Compliance with industry standards is a major driver: **HIPAA** (Healthcare), **PCI-DSS** (Finance), or **ISO/IEC 27001** (Security Management).

### Defining Criteria for Success and Failure

- Establish what goals must be met for stakeholders to be satisfied (e.g. employee productivity vs revenue increase).
- Ascertain the consequences of failure: how visible is the project to executives, and how much would unforeseen downtime disrupt operations?

## Constraints, Scheduling, and Politics

### Analysing Business Constraints

- **Budgetary:** allocations for CapEx (equipment) vs OpEx (staffing, maintenance, training).
- **Staffing:** assessment of in-house expertise; complex protocols (like OSPF) should not be recommended if the staff lacks the skills to operate them.

### Project Scheduling and Milestones

- Defining final due dates and essential intermediate milestones to detect slips early.
- Accounting for long lead times, such as circuit capacity changes or new site provisioning.

### The "Eighth Layer": Workplace Politics

- Understanding hidden agendas, turf wars, or **"technological religions"** (biases toward specific vendors or protocols).
- Identifying project advocates vs opponents and understanding risk tolerance within the corporate style.

## Applications: The Reason Networks Exist

- Requirement analysis must identify both current applications and planned future rollouts.
- Each application should be ranked by criticality: **1 (Extremely Critical) to 3 (Not Critical)**.

### Application Profiling and Categorisation

- **Real-Time/Interactive:** high sensitivity to delay and jitter (VoIP, Video).
- **Transaction/Bulk Data:** high bandwidth and integrity needs (database syncs, backups).
- **Standard Business:** burst traffic with moderate bandwidth (Email, Web).

### Technical Metrics for Real-Time Apps

- **One-way latency:** < 150 ms.
- **Jitter (delay variation):** < 30 ms.
- **Packet loss:** < 1%.

### System vs User Applications

- **User apps:** E-commerce, CAD, CRM, sales tracking.
- **System apps:** authentication (802.1X), naming (DNS), network management (SNMP), backup.

## Scalability, Availability, and Security Requirements

### Scalability Requirements

- Designing for growth over a **3-to-5-year horizon** to prevent premature obsolescence.
- Distinguishing between **Scaling Out** (horizontal — adding components) and **Scaling Up** (vertical — upgrading links/devices).

### Availability and Reliability Modelling

- Businesses often demand **"Five Nines" (99.999%)**, which permits only ~5.26 minutes of downtime per year.
- Calculated via: **Availability = MTBF / (MTBF + MTTR)**.

### Security Requirements (Zero Trust)

- Moving from perimeter models to **Zero Trust** architectures where no part of the network is inherently safe.
- Requirements for micro-segmentation, traffic isolation (VRFs/VLANs), and identity-based access.

### Performance Specifications

- Establishing thresholds for throughput, utilisation, and call/connection blocking rates.
- Developing a **"Factor of Safety"** to compensate for uncertainty in performance estimates for new designs.

## Data-Gathering Methods

### Qualitative Methods

- Stakeholder interviews with both executives (vision) and admins (operational pain points).
- Review of existing documentation: network topologies, asset registries, and security policies.

### Quantitative Methods: Active Monitoring

- Utilising **NetFlow/IPFIX** to build a map of "top talkers" and protocol mixes.
- **SNMP polling** for historical port utilisation, CPU spikes, and memory exhaustion.

### Deep Packet Inspection (DPI)

- Using tools like Wireshark to diagnose application behaviour and identify if delays are network-based or application-based.
- Establishing a baseline of existing network traffic before proposing upgrades.

### User and Device Density Analysis

- Determining **concurrent user peaks** rather than average loads.
- Modern standard baseline: **3:1 device-to-user ratio** (laptop, phone, tablet); plan for around 2.5 to 3 devices per concurrent user over a 3-to-5-year horizon.

## Prioritisation and Decision Frameworks

### The Hierarchy of Needs in Network Design

Ordered priorities:

1. Connectivity
2. Integrity (Reliability/Security)
3. Interoperability
4. Service Delivery
5. Autonomy

Ensures sophisticated features are not built on a fragile base.

### Principles of Conflict (Trade-off Matrix)

- **KISS vs Redundancy:** additional redundancy increases complexity; resolution is driven by availability needs.
- **Flexibility vs Usability:** more flexible systems are often harder to use and operate.

### Decision Matrices and Trees

- **Decision matrix:** weighing design options against business priorities (e.g. cost savings vs scalability).
- **Decision tree:** simplified logic for technology selection (e.g. choosing BGP vs IGP based on domain boundaries).

### Strategic vs Tactical Planning

- **Strategic:** targeting long-term goals (e.g. core migration to MPLS).
- **Tactical:** overcoming short-term issues or temporary needs (e.g. temporary partner access).

## Quantifying and Costing the Design

### The Traffic Demand Matrix

- Quantifying application behaviour into a source-destination matrix.
- Essential for sizing links (e.g. determining the number of T1 lines needed to meet a 50% utilisation goal).

### Project Costing and Economics

- Utilising **Net Present Value (NPV)** to compare design alternatives over the project lifecycle.
- Factoring in depreciation of assets and the "time value of money".

## The Requirements Specification Document

- The **"source of truth"** that must be signed off by stakeholders before logical design begins.
- Includes scope, performance targets, application inventory, and growth factors.

## Core Design Principles and Next Steps

- **Build with modularity and hierarchy:** divide the network into functional blocks (PINs) to simplify expansion and isolate faults.
- **Ensure fault isolation:** design boundaries to prevent local failures from propagating across the entire enterprise domain.
- **Adopt a holistic approach:** view the network as an interconnected whole rather than disconnected "communication islands."

Requirement analysis ensures the network is a business enabler rather than a cost centre. Successful analysis transitions into **Logical Design**, mapping requirements to topology, addressing, and security plans.
