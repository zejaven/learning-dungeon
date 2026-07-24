# Logical Design — Network Design Process

Logical network design is the phase focused on **how data flows, how devices are grouped, and how traffic is directed**. It is the conceptual framework that determines network behaviour before any hardware implementation — the **"decision layer"** that defines the rules of operation and communication patterns.

![Infographic: The Blueprint of Logical Network Design. Presents the three pillars of logical design (structured IP addressing & VLSM, logical segmentation via VLANs, dynamic path selection) with a routing protocol comparison, the hierarchical LAN model (Core, Distribution, Access layers), and optimisation/resiliency techniques such as route summarisation and virtualisation for redundancy.](images/slide02-blueprint-of-logical-network-design.png)

## The Three Pillars of Logical Design

- **Structured IP Addressing & VLSM:** use Variable Length Subnet Masking (VLSM) to prevent address waste and allow for 20%–50% growth.
- **Logical Segmentation via VLANs:** VLANs isolate sensitive traffic (e.g. HR/Finance) to improve security and reduce broadcast domain noise.
- **Dynamic Path Selection:** Interior Gateway Protocols (IGPs) like OSPF or EIGRP determine the best path for data within an organisation.

## Logical vs Physical Design

- **Physical:** cables, switches, access points, racks, and ports — what you can touch.
- **Logical:** IP ranges, VLANs, routing paths, access rules, and traffic flow — what makes the network function.
- The goal is designing for **logical complexity within physical simplicity** (e.g. one switch stack supporting five logical zones).

## Guiding Principles and Business Alignment

- **Business-Centricity:** design infrastructure to fit the business, rather than forcing the business to fit existing infrastructure.
- **Scalability:** ensure the network grows without becoming an unmanageable "mess".
- **Modularity:** use repeatable logical patterns to allow on-demand growth.

Requirement analysis is a **fact-collection** exercise: identify critical applications, user departments, and traffic expectations; define uptime, latency, and security expectations based on specific industry needs (e.g. healthcare vs retail); gather requirements from technical and non-technical stakeholders.

## The Hierarchical Model in Logical Design

- **Access layer (the edge):** the network entry point where end-user devices such as PCs and IP phones attach; provides endpoint access and enforces initial policies.
- **Distribution layer (policy):** aggregates access blocks, terminates Layer 2 broadcast domains, and provides routing boundaries, security policies, and Quality of Service (QoS).
- **Core layer (backbone):** the high-speed transit point connecting distribution blocks, the data centre, and the Internet edge.

### Multi-Tier LAN Design Models

- **Three-tier design:** separate Access, Distribution, and Core layers for maximum fault isolation and backbone connectivity.
- **Two-tier (collapsed core) design:** merges the Distribution and Core roles for smaller scale or single-building campuses.
- **Selection criteria:** network scale, application demands, and cost.

### Hierarchical Logical Grouping

- Design multi-level hierarchies (e.g. an initial level for regional routing, sub-levels for subsidiaries or PoPs).
- Match the logical hierarchy to business operational needs.
- Use tags and pre-defined properties at each hierarchy level to simplify deployment.

## The IP Addressing Blueprint

A structured addressing plan is the **foundation of network communication**; it prevents conflicts and unmanageable routing tables.

- Addresses divide into **Network** and **Host** portions.
- Strategic use of **RFC 1918 private address spaces** (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16) provides internal flexibility.

### Hierarchical Addressing & Summarisation

- **Postal system analogy:** structure addresses logically (Country > State > City) so routers forward based on broad prefixes.
- **CIDR & summarisation:** advertise multiple smaller networks as a single summary route to reduce routing table size and CPU overhead.
- Summarisation stabilises the core by **hiding local link flaps** from the rest of the system.

### Subnetting and VLSM Logic

- Move from fixed-length subnetting to **Variable Length Subnet Masking (VLSM)**.
- Customise IP blocks to match specific host requirements, avoiding address waste.
- Always allocate for **20% to 50% growth over a 3-to-5-year horizon**.

Step-by-step VLSM design methodology:

1. List requirements for every segment.
2. Sort subnets from largest host requirement to smallest.
3. Allocate starting from the beginning of the IP block to ensure contiguous space.

### IP Address Management (IPAM) Best Practices

- A unified approach to IP addresses, VLANs, DNS records, and DHCP leases.
- Automate DNS updates (A, PTR, CNAME records) upon IP allocation.
- Maintain centralised visibility to prevent conflicting assignments and overlapping subnets.

### Transitioning to IPv6

- Addresses IPv4 exhaustion by moving toward a **128-bit address space**.
- **Features:** auto-configuration (SLAAC), built-in IPsec, and elimination of the need for NAT.
- **Shift in logic:** moving from address conservation to clean, hierarchical grouping (e.g. a standard /64 for every end-user subnet).

## Logical Segmentation with VLANs

VLANs segment physical networks into multiple distinct logical networks. Each VLAN is a logical broadcast domain: devices on different VLANs cannot talk directly even if they sit on the same physical switch.

Strategic rationales for VLAN segmentation:

- **Security:** isolating sensitive departments (Finance/HR) from general or Guest traffic.
- **Performance:** containing broadcast chatter to optimise CPU cycles across the network.
- **Regulatory compliance:** meeting data isolation standards like PCI-DSS or HIPAA.

### VLAN Trunking and 802.1Q Tagging

- Trunks carry traffic for multiple VLANs over a single physical link.
- **IEEE 802.1Q** inserts a 4-byte tag into Ethernet frame headers to maintain logical identity.
- **Best practice:** implement unused/unique native VLANs to mitigate security risks like VLAN-hopping.

### Inter-VLAN Routing Architectures

Moving traffic between isolated logical networks happens at Layer 3:

- **Router-on-a-Stick:** logical sub-interfaces on a single physical router port.
- **Layer 3 switches:** Switch Virtual Interfaces (SVIs) for hardware-speed routing.

## Routing Strategy

### Static vs Dynamic Routing

- **Static:** manual configuration; high security and low overhead, but it does not scale.
- **Dynamic:** protocols discover topology maps and adapt to failures; essential for redundant paths.

### Algorithmic Foundations: Distance Vector vs Link State

- **Distance Vector (Bellman-Ford):** routers share tables with neighbours; simple but slower convergence.
- **Link State (Dijkstra):** routers maintain a complete map of the entire topology; faster convergence and less prone to loops.

| | Distance Vector (e.g. RIP) | Link State (e.g. OSPF) |
|---|---|---|
| **Convergence** | Slower | Faster |
| **Network view** | Local (neighbour tables) | Full topology map |
| **Scalability** | Limited | High |

### Interior Gateway Protocol (IGP) Strategy

- **OSPF:** link-state protocol; highly scalable open standard using "Cost" based on bandwidth.
- **EIGRP:** advanced distance-vector (hybrid); fast convergence using the DUAL algorithm and composite metrics.
- **Strategic goal:** deploy a **single IGP** across the enterprise for operational simplicity.

### Exterior Gateway Protocol (EGP) — BGP

- Used for communication between distinct organisations or Autonomous Systems (AS).
- A path-vector protocol based on administrative policies rather than just link speed.

### Routing Policy and Security

- Implement **MD5 authentication** for protocol neighbours to secure adjacencies.
- Use **Passive Interfaces** on ports connected to end-devices to reduce CPU load and security risks.
- Summarise at major network boundaries to stabilise core routing.

## Quality of Service (QoS) Design

- Prioritise time-sensitive traffic (voice/video) over elastic data flows.
- **The 12-Class Model (RFC 4594):** defines specific markings like EF (VoIP), CS4 (Real-time Video), and CS1 (Scavenger).
- Manage "Best Effort" default classes, reserving ~25% of bandwidth.

QoS mechanics:

- **Policing:** rate-limiting traffic at the trust boundary to prevent congestion.
- **Queuing:** differentiated treatment within the switch fabric (e.g. Priority Queues vs Guaranteed Bandwidth Queues).
- Consider hardware-based vs software-based QoS implementation.

## High Availability Frameworks

- **Network resiliency:** redundant parallel paths simplified by bundling into EtherChannels/MEC.
- **Device resiliency:** Non-Stop Forwarding (NSF) and Stateful Switchover (SSO) for transparent supervisor failover.
- **Operational resiliency:** In-Service Software Upgrades (ISSU) for zero-downtime maintenance.

### Logical Redundancy: VSS and StackWise

- **Virtual Switching System (VSS):** clusters two physical chassis into a single logical virtual switch.
- **StackWise:** stacks multiple fixed-configuration switches into one logical entity with a single control plane.
- **Benefits:** simplified STP topology, increased bandwidth, and sub-second recovery.

## IP Multicast Logic

- Conserves bandwidth for one-to-many communication.
- **Distribution trees:** Source Trees (SPT) vs Shared Trees (rooted at Rendezvous Points).
- **IGMP Snooping:** lets Layer 2 switches make intelligent forwarding decisions for multicast groups.

## Common Pitfalls

- **Pitfalls:** overcomplication, poor IP planning, and assuming redundancy exists without testing it.
- Written, reviewed documentation is a necessity: address maps, topology diagrams, and route logic.
- Design for change, not just the current headcount.
