# Clos (Leaf-Spine) Architecture — Architectural Models

## Historical Context: The 1953 Breakthrough

- Invented by Edson Erwin (1938) and formalised by Charles Clos (1953) at Bell Labs.
- Originally designed for telephone switching systems to ensure non-blocking voice connections.
- The goal: move data from any input port to any output port without contention, regardless of system load.

### The Scaling Problem: N² Crosspoints

- Traditional single-stage crossbar switches scale quadratically.
- Example: connecting 10,000 subscribers would require 100,000,000 mechanical crosspoints — a physical and economic impossibility.
- Clos proposed a **multistage switching network** to reduce the total number of crosspoints while maintaining performance.

## Topology Parameters and Non-blocking Conditions

Clos networks are defined by three integers:

- **n:** number of sources feeding into each ingress switch.
- **m:** number of middle-stage switches.
- **r:** number of ingress/egress stage switches.

The network implements an *r*-way perfect shuffle between stages.

### Strict-Sense Non-blocking Networks

- **Definition:** an unused input can always connect to an unused output without rearranging existing calls.
- **The Clos Condition:** for a three-stage network to be strict-sense non-blocking, **m ≥ 2n − 1**.
- **Worst-case logic:** if an input switch has *n − 1* active calls and the destination output switch has *n − 1* active calls, you need *2n − 1* middle switches to guarantee a free path.

### Rearrangeably Non-blocking Networks

- **Definition:** an unused input can always connect to an unused output, but existing calls may need to be rerouted to different middle switches.
- **Condition:** **m ≥ n**.
- Utilises Hall's Marriage Theorem and König's link colouring theorem for proof of existence.
- Commonly used in data centres to balance performance and economics.

## The Shift in Data Centre Gravity

- **Historical traffic:** North-South (80%) — client-to-server traffic leaving the data centre.
- **Modern traffic:** East-West (80%) — lateral server-to-server traffic driven by virtualisation, microservices, and AI.
- Traditional 3-tier models (Core, Aggregation, Access) fail under heavy East-West loads due to hierarchical bottlenecks.

## Structural Rules of the 2-Tier Fabric

- **Tier 1: Spine layer.** The backbone that routes traffic between leaves; no servers connect here.
- **Tier 2: Leaf layer.** The access point for all servers, storage, and firewalls.
- **Constraint 1:** no horizontal links (Spine-to-Spine or Leaf-to-Leaf).
- **Constraint 2:** complete bipartite interconnection (every leaf connects to every spine).

### Comparing Models: 3-Tier vs Leaf-Spine

- **3-Tier:** relies on Layer 2 switching and Spanning Tree Protocol (STP).
- **Leaf-Spine:** collapses the hierarchy into two tiers and uses Layer 3 routing.
- **Key advantage:** flattening the network reduces hops and complexity.

### Predictable Latency: The Two-Hop Guarantee

- Every server is exactly the same number of network hops away from any other server.
- Path: Leaf 1 > Spine > Leaf 2.
- Critical for latency-sensitive workloads like distributed databases and GPU clusters.

## Horizontal Scaling (Scale-Out)

- Unlike 3-tier networks that scale "up" (bigger, expensive chassis), Clos scales "out".
- To add server capacity: add a Leaf switch.
- To add bandwidth: add a Spine switch.
- Redundancy: the network remains operational even if a spine switch fails.

### Scaling Limits and Port Density

- Fabric size is constrained by physical port density.
- **Constraint 1:** number of spines ≤ number of uplink ports on a leaf.
- **Constraint 2:** number of leaves ≤ number of ports on a spine.
- **Example:** 32-port spines can support a maximum of 32 leaves.

### Scaling Beyond Two Tiers: 5-Stage Clos

- Necessary for hyperscale facilities exceeding single-pod spine density.
- Adds a **Super-Spine (Core)** layer above multiple spine layers.
- Path: Leaf > Spine > Super-Spine > Spine > Leaf.

## Load Balancing: From STP to ECMP

### The Demise of Spanning Tree (STP)

- STP prevents loops by blocking redundant links, leaving up to 50% of expensive bandwidth idle.
- Clos fabrics eliminate the need for STP by using IP routing (L3) or large-scale bridging (TRILL/SPB).

### Protocol Hero: Equal-Cost Multi-Path (ECMP)

- Replaces STP by treating all redundant paths as active and equal-cost.
- Enables load balancing across all available spine switches simultaneously.
- Maximises data throughput by utilising 100% of links.

### ECMP Hashing and Flow Integrity

- Deterministic hashing of the packet's 5-tuple: Source IP, Dest IP, Source Port, Dest Port, Protocol.
- Ensures that packets belonging to a single "flow" take the same path to prevent out-of-order delivery.
- Efficiently distributes thousands of independent flows across the fabric.

### Understanding Oversubscription Ratios

- **Definition:** ratio of downlink bandwidth (to servers) vs uplink bandwidth (to spines).
- **Formula:** [Number of Downlinks × Speed] / [Number of Uplinks × Speed].
- A ratio of **1:1** is truly non-blocking; common practical designs use **3:1** to balance cost.

## Performance Limits and Congestion Management

### Performance vs the Ideal Macro-Switch

- In theory, Clos networks emulate a single, massive non-blocking macro-switch.
- Equivalence holds true for splitable flows or single-flow per source/destination.
- Modern data centres break these assumptions: flows are non-splitable, and congestion control is required.

### The Impossibility Results (Sherry et al.)

- Routing for max-min fairness in Clos networks fails to replicate macro-switch performance.
- **Proof:** optimising for max-min fairness can reduce some flow rates by a factor of N (where N is the number of middle switches).
- Throughput-maximising allocations often zero out the rates of most flows to achieve gains.

### ECMP's Fatal Flaw: Localised Decisions

- ECMP makes purely local decisions based on hashing, unaware of downstream congestion or link failures.
- Hash collisions can cause "hot spots" if several large **"elephant" flows** are assigned to the same spine.
- Can reduce delivered traffic by up to **40%** during link failures despite built-in redundancy.

### Congestion-Aware Load Balancing: CONGA

- A "leaf-to-leaf" feedback mechanism that conveys real-time path congestion.
- Source leaves detect flowlets and route them via the least congested path.
- Handles asymmetric topologies caused by failures significantly better than static ECMP.

### Performance Impact of Buffering

- Deep-buffer switches are often needed for bursty "Big Data" or Incast traffic patterns.
- Research indicates it is more effective to apply additional buffering in the leaf tier than the spine tier to control Incast.
- Fabric link speeds (e.g. 40G/100G) relative to edge speeds (10G) improve ECMP efficiency.

## L3 Fabric Design: Underlay, Overlay, and Protocols

### Underlay vs Overlay

- **Underlay:** the physical L3 leaf-spine network using routed point-to-point links (eBGP, OSPF).
- **Overlay:** a virtual L2 network (e.g. VXLAN) running over the L3 underlay to allow VM mobility.

### Protocol Choice: eBGP in the Data Centre

- eBGP is recommended for simplicity and ease of load sharing via ECMP.
- Common design: one Autonomous System Number (ASN) for all spines, and discrete ASNs for each leaf.
- Enables easy troubleshooting: each rack is identifiable by its ASN in traceroute commands.

### Handling L2 Adjacency: EVPN-VXLAN

- Used when applications require L2 connectivity across different racks.
- Leaf switches act as VXLAN Tunnel Endpoints (VTEPs), encapsulating L2 frames into IP packets.
- BGP-EVPN serves as the control plane for distributing MAC/IP reachability.

## Hardware Deployment and Redundancy

### Fixed vs Modular Hardware

- **Fixed switches:** high-density "Top-of-Rack" (ToR) units with a single ASIC.
- **Modular chassis:** used for Spines in large pods, offering higher port density and resiliency.
- Modern best practices use high-capacity 800G spines with high-density 10G/25G leaves.

### Redundancy and MLAG

- Servers are often dual-homed to two leaf switches using MLAG (Multi-chassis Link Aggregation).
- Ensures server-level redundancy without creating L2 loops in the Clos fabric.

## Research Spotlight: Starfish — Optimising Small-Scale Data Centre Networks

How the Starfish co-design outperforms traditional leaf-spine by leveraging existing hardware more efficiently.

![Infographic: Starfish — Optimising Small-Scale Data Centre Networks. The left panel covers the DRing topology's structural efficiency (50% reduction in oversubscription, simplified block-based cabling, built-in fault tolerance); the right panel covers SU-K routing for intelligent traffic flow, plus a network performance and scaling comparison of Starfish, Jellyfish, and Dragonfly.](images/slide02-starfish-small-scale-data-centre.png)

**DRing topology — structural efficiency:**

- **50% reduction in oversubscription:** distributing servers across all switches allows each rack to handle double the throughput of leaf-spine.
- **Simplified block-based cabling:** switches are organised into "supernodes," allowing for clean, bundled wiring and easy incremental expansion.
- **Built-in fault tolerance:** structural uniformity ensures that random link or switch failures do not cause disproportionate performance drops.

**SU-K routing — intelligent traffic flow:**

- **Shortest-Union-K (SU-K) selection:** uses both shortest and near-shortest paths to maximise fabric capacity for close-range rack pairs.
- **Adaptive traffic weighting:** a central controller optimises path weights based on real-world demands to prevent congestion.
- **Increased supported traffic:** Starfish consistently outperforms traditional designs across university, enterprise, and social media traffic traces.

**Network performance and scaling comparison:**

| Topology | Traffic Gain (vs Leaf-Spine) | Primary Scaling Focus |
| --- | --- | --- |
| Starfish | +56% | Small-scale efficiency |
| Jellyfish | +52% | Large-scale expansion |
| Dragonfly | <0% (at small scale) | High-performance computing |

## Key Takeaways

- **Two-tier design:** Spine (backbone) and Leaf (access).
- **Predictable:** always two hops server-to-server.
- **Scalable:** scale horizontally by adding more switches.
- **Efficient:** uses ECMP and L3 routing to keep all links active.
- **Future-ready:** foundation for EVPN-VXLAN virtualised environments.
