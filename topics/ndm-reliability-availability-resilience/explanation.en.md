# Reliability, Availability, and Resilience — Core Principles of Network Design

High availability is a system characteristic aiming to ensure an agreed level of operational performance, typically uptime, for a higher than normal period. In modern enterprise environments, the network is the fundamental infrastructure or "circulatory system" underpinning all digital operations. Failure to build a "rock-solid" foundation at the network level eventually leads to performance and reliability challenges for all dependent applications, such as IP telephony and video. The blueprint for resilient design is achieving **"Five Nines" (99.999% availability)** through strategic Mean Time Between Failures (MTBF) increase and Mean Time To Repair (MTTR) reduction.

![Infographic: High Availability — The Blueprint for Resilient Network Design. Covers the mathematics of uptime (availability formula, the exponential cost of "the nines", serial vs. parallel reliability) and engineering the resilient architecture (the three-tier hierarchical model, eliminating single points of failure, active-active vs. active-passive).](images/slide02-high-availability-blueprint.png)

## Reliability vs Availability

- **Reliability (R(t))** is the probability that a component or path will perform its function without failure over a specific time interval under stated conditions.
- **Availability (A)** is the statistical probability that a system is operational and accessible to process requests at any given point in time.
- While a reliable device rarely breaks down, an available system factors in both frequency of failure and speed of recovery.

## The Mathematics of Uptime: MTBF and MTTR

- **Mean Time Between Failures (MTBF)** represents the average operational time expected between intrinsic, non-destructive hardware failures.
- **Mean Time To Repair (MTTR)** is the average time required to troubleshoot, repair or replace a component, and restore the system to full operation.
- Steady-state availability is defined by the formula: **A = MTBF / (MTBF + MTTR)**.

Availability can be enhanced by either increasing MTBF through the procurement of higher-quality components or decreasing MTTR. Because physical repairs can take hours, modern network design focuses heavily on **automating recovery to drive MTTR down to milliseconds**. A true availability measure must be holistic, accounting for select application functions that might fail even if hardware remains "up".

### Quantifying the "Nines" of Availability

Availability is often expressed as a percentage of uptime, where "Five 9s" (99.999%) is considered the carrier-grade gold standard. The cost of each additional nine grows exponentially:

| Level | Availability | Annual downtime | Cost profile |
| --- | --- | --- | --- |
| **Two Nines** | 99.0% | 3.65 days | Low cost; single points of failure likely |
| **Four Nines** | 99.99% | 52.6 minutes | High cost, fully redundant hardware |
| **Five Nines** | 99.999% | 5.26 minutes | Extreme cost; carrier-grade, specialised hardware, automated failover |

### Serial vs Parallel Availability

- **Serial topology:** where a signal must pass through multiple dependent devices sequentially, total availability is the product of each component's availability: **A_total = A₁ × A₂ × … × Aₙ**. Serial dependencies systematically erode availability; for example, three devices at 99.9% availability result in a combined availability of only 99.7%.
- **Parallel topology:** identical redundant components where the system only fails if all parallel components fail simultaneously: **A_total = 1 − ∏ (1 − Aᵢ)**. By converting a serial dependency into a parallel block, the theoretical availability can leap from three nines to six nines (99.9999%).

## Eliminating Single Points of Failure (SPOF)

- A **SPOF** is any individual component or path whose failure results in the immediate disruption of the entire system's dependency chain.
- Identifying SPOFs requires **Failure Mode and Effect Analysis (FMEA)**, which lists potential failure modes and assigns risk levels.
- Resilient designs use targeted structural redundancy to ensure data always has an alternative pathway — for example **LACP** (Link Aggregation) and **FHRPs** (HSRP/VRRP for gateway abstraction) convert a SPOF vulnerability into a resilient path.

## The Hierarchical Network Design Model

The three-tier hierarchical model provides a scalable and manageable framework for high availability:

- **Core (Backbone):** high-speed switching, redundancy.
- **Distribution (Policy):** policy enforcement, routing between access and core.
- **Access (Endpoint):** connects end devices, implements QoS.

Hierarchy clarifies the role of each device, making it simpler to deploy, manage, and isolate fault domains. This modularity allows building blocks to be taken out of service for maintenance without impacting the rest of the network.

## Resilience Across the OSI Layers

### Layer 1: Physical Infrastructure Resilience

- Resilience starts with **Power Domain Isolation**, using dual internal, hot-swappable Power Supply Units (PSUs).
- Each PSU should draw from isolated circuits, such as one to utility power and one to a generator-backed Uninterruptible Power Supply (UPS).
- **Geographic Route Diversity** ensures that redundant fibre-optic runs enter a facility via separate ingress points to avoid **"backhoe fade"** from accidental excavation.

### Layer 2: Data Link Redundancy and Loop Prevention

- **Link Aggregation (LACP/EtherChannel)** bundles multiple physical ports into a single logical channel, providing increased bandwidth and preventing single-link failures. If a physical wire fails, the system redistributes traffic across remaining links in microseconds without triggering routing reconvergence.
- **UniDirectional Link Detection (UDLD)** must be used on fibre links to prevent loops caused by one-way communication errors.
- Introducing parallel components creates physical loops, which can lead to catastrophic **broadcast storms** that consume 100% of bandwidth. **Rapid Spanning Tree Protocol (RSTP/802.1w)** is required to dynamically block redundant paths and open them only when an active link fails.
- Best practices include using the Spanning-Tree toolkit (**Root Guard, BPDU Guard**) to protect against unexpected topology changes.

### Layer 3: Network Layer Resilience

- **Dynamic Routing Protocols (EIGRP, OSPF)** establish neighbour adjacencies through keepalive messages to sense link degradation.
- If a failure occurs, routers flush invalid paths and execute algorithms like Dijkstra's Shortest Path First to find alternate routes.
- **EIGRP** is often preferred in campus environments for its faster convergence and flexible route summarization.

### First Hop Redundancy Protocols (FHRP)

- End stations typically rely on a static default gateway; if that single router fails, the devices are stranded.
- FHRPs like **HSRP** (Cisco-proprietary) or **VRRP** (open standard) provide a single Virtual IP (VIP) and MAC address for an active/standby pair.
- **Gateway Load Balancing Protocol (GLBP)** allows for active-active redundancy by sharing the traffic load between a group of routers.

## Hardware-Level Redundancy: Supervisor Engines

- Modular chassis switches support dual Supervisor Engines to protect the control plane.
- **Stateful Switchover (SSO)** synchronizes protocol state and forwarding information between the active and standby supervisors.
- **Non-Stop Forwarding (NSF)** works with SSO to allow packets to continue being forwarded during a supervisor switchover, avoiding routing adjacency resets.

## Failover Topologies and Standby States

**Active-Passive vs Active-Active:**

- **Active-Passive** designs involve one device servicing 100% of workloads while a redundant counterpart sits idle in standby mode, ready to take over.
- **Active-Active** clusters maximize hardware utilization by having all nodes share the workload concurrently. They require meticulous capacity planning: individual components must never exceed **50% load** to avoid cascading failure if one node drops.

**Failover operational states:**

- **Cold Standby:** the secondary system remains offline or idle, requiring manual activation and longer recovery times (minutes to hours).
- **Warm Standby:** the system is partially active and regularly updated but does not serve traffic under normal conditions.
- **Hot Standby:** backup systems are fully operational and synchronized in real time, offering near-zero recovery time.

## Convergence Tuning for High Availability

- To achieve sub-second convergence, designers must **build triangles, not squares**, utilizing point-to-point Layer 3 links.
- Hardware-based link failure detection is significantly faster and more deterministic than software-based timers.
- Tuning HSRP/GLBP millisecond timers and optimizing Cisco Express Forwarding (CEF) hashing helps avoid routing black holes.

## The "Redundancy Paradox"

- Adding duplicate hardware doubles or triples Capital Expenditure (CapEx) and increases Operational Expenditure (OpEx) through higher power and cooling costs.
- Unmanaged redundancy creates architectural complexity, which can trigger failures (like loops) faster than a simple component failure would.
- High complexity also increases the risk of **human error**, which is a leading cause of network outages.

## Measuring and Interpreting Availability

### Defects Per Million (DPM)

- The DPM method describes the number of failures occurring during one million hours of network running time.
- Unlike the percentage method, DPM is effectively used to report issues in existing large networks, including partial outages.
- Calculation: **DPM = (1,000,000 / Accumulated Operating Hours) × Number of Failures**.

### The "Watermelon Effect"

- A system may report high statistical uptime (green on the outside) while having capacity issues that hide stress failures (red on the inside).
- An outage during peak usage periods is devastating even if the annual availability percentage remains technically "high".
- Holistic availability measurement must ideally use highly available monitoring tools to provide accurate data.

## Advanced Resilience and Related Concerns

- **Self-healing clusters:** modern high availability designs use self-healing concepts where systems automatically rebalance data and workloads after a component fails, reducing the need for immediate human intervention by reconstituting backups for future failures. Distributed databases like **Aerospike** use **shared-nothing architectures** to eliminate centralized masters that could bottleneck failover.
- **Quality of Service (QoS) and availability:** high availability aims to protect mission-critical applications from network congestion. QoS mechanisms ensure that real-time data like voice and video are given higher priority during periods of link saturation. Transmit queue (TX-queue) starvation can cause dropped traffic even if a link is up; QoS mitigation is essential for true availability.
- **Planned vs unplanned downtime:** unplanned outages arise from physical events like hardware crashes, software errors, or environmental anomalies; planned outages result from management-initiated events, such as software upgrades or hardware patches. True High Availability requires technologies like **In-Service Software Upgrade (ISSU)** to maintain service during planned maintenance.

## Key Takeaways

- **Eliminate Single Points of Failure** through multi-tiered redundancy across the OSI model.
- **Use a Hierarchical, Modular Model** to isolate fault domains and simplify troubleshooting.
- **Automate Detection and Failover** to drive MTTR toward milliseconds and achieve "Five 9s".
- **Balance Redundancy against Complexity**, ensuring that the cost of availability aligns with business risk.
