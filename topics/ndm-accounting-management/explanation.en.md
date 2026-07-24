# Accounting Management — Network Management

Accounting is the third pillar of the ISO FCAPS structural model (Fault, Configuration, Accounting, Performance, Security), defining the essential tasks for managing modern, highly connected network environments. Beyond simple billing, it is the comprehensive **science of measuring, log-aggregating, and analysing network resource consumption**, transforming raw packet traffic into actionable metadata for engineering and administration — while ensuring security, auditing, and transparency.

![Infographic: Network Accounting Management — The Science of Resource Tracking. The left column shows the AAA framework (Authentication, Authorisation, Accounting); the right side shows the accounting log lifecycle from session start to forensics, plus an industry-standard protocol comparison of RADIUS, TACACS+, and NetFlow.](images/slide02-science-of-resource-tracking.png)

## Core Objectives and the Business Case

- **The "Who, What, Where, and How":** accounting tracks exactly who performed what actions, where they occurred, and for how long. It answers essential questions regarding data volume, connection time, and specific resource utilisation.
- **The business case:** it facilitates usage-based billing, peering agreements, and security analysis, and is essential for network monitoring, anomaly detection, and capacity planning.

## Accounting within the AAA Framework

Accounting is the third pillar of the Authentication, Authorisation, and Accounting (AAA) framework. Accounting data is only meaningful when securely linked to a verified identity. The pillars are sequential:

1. **Authentication — the entry gate:** "Is the user who they claim to be?" Verifies identity credentials.
2. **Authorisation — the policy engine:** "What resources is this user permitted to touch?" Defines which VLANs, ports, or applications the verified identity can access.
3. **Accounting — the continuous ledger:** "What did the user actually do, and what was consumed?" Logs consumption metrics and commands executed.

**The electronic "ledger" concept:** accounting acts as a continuous camera and ledger, logging consumption and executed commands after access is granted. It provides the "audit trail" necessary for security history.

## Key Accounting Metrics

- **Data volume:** total bytes uploaded/downloaded.
- **Temporal metrics:** precise login/logout times and total session duration.
- **Resource metrics:** CPU time, storage occupancy, or service-specific usage (e.g. VoIP vs web).

## Where to Account? Layered Analysis and Granularity

Accounting can be performed at different network layers, each with trade-offs:

- **Data link layer:** measures all frames but includes broadcast/management overhead.
- **Network layer:** common for IP traffic but can make protocols like SSH appear "expensive" due to header inclusion.
- **Application layer:** fairest for users; common at proxies as it ignores LAN overhead.

**Granularity of accounting points:** accounting can be performed per Subnet (useful for department chargebacks), Host, User (via authenticating proxies), or Switch Port.

## Industry-Standard Accounting Protocols

| Protocol | Primary use case | Transport & port | Data scope |
|---|---|---|---|
| **RADIUS** | Distributed edge (Wi-Fi/VPN) | UDP 1813 | Session-level metadata (volumes, duration) |
| **TACACS+** | Device administration | TCP 49 | Command-level auditing (CLI inputs) |
| **NetFlow** | Traffic profiling | UDP (e.g. 9996) | Flow-level metrics (IP pairs, packet rates) |

### RADIUS: Optimised for Distributed Access

- Runs over UDP (port 1813 for accounting), optimised for edge networks like campus Wi-Fi and VPN endpoints.
- **Mechanisms:** uses Attribute-Value Pairs (AVPs) in Accounting-Request frames; tracks session-level metadata with Start, Interim-Update, and Stop status types.

### TACACS+: Optimised for Device Administration

- Cisco-developed protocol running over TCP (port 49), predominantly used for managing administrative access to core routers and switches.
- **Command-level auditing:** unlike RADIUS, TACACS+ logs every single command typed into a CLI by an administrator, providing an immutable audit trail for identifying accountability in configuration errors.

### NetFlow and IPFIX: Flow-Based Accounting

- Focuses on unidirectional traffic "flows" rather than just user sessions.
- Flows are identified by a **7-key tuple:** Source/Dest IP, Source/Dest Port, L3 Protocol, ToS byte, and Ingress interface.
- **Architectural components:**
  - **Exporter:** monitors packets and creates flow records.
  - **Collector:** stores records from multiple exporters in a central database.
  - **Analyser:** performs deep statistical auditing for forensics and capacity planning.

## The Lifecycle of an Accounting Log

1. Initialisation (trigger).
2. **Accounting-Start:** a 'Start' packet records timestamps and IDs.
3. **Interim-Updates:** 'Interim-Update' packets prevent data loss during crashes.
4. **Accounting-Stop (termination):** a terminal 'Stop' packet specifies final byte counts, total session time, and termination reasons.
5. Data aggregation/billing.

## Business and Operational Applications

- **Internal chargebacks and cost allocation:** shared IT services use accounting to bill business units based on bandwidth/compute consumption (e.g. via VLAN tags), preventing one department from monopolising shared corporate resources.
- **Fair Use Policies (FUP) and capacity management:** accounting data triggers automated rules to throttle speed or redirect users who exceed data caps — essential for Service Level Agreement (SLA) enforcement.
- **Forensic auditing and compliance:** accounting logs pinpoint the vector of unauthorised data breaches and provide permanent legal records mandated by global regulations: HIPAA (ePHI access in healthcare), PCI-DSS (cardholder data in payments), and SOX.
- **Optimisation of network resources:** accounting tackles administrative tasks to optimise distribution for users and identifies wasteful spending on unused applications.

## The Hierarchical Model

- **Element Management Layer (EML):** the lowest layer, where individual network elements (routers/switches) generate accounting data via Syslog, SNMP, or NetFlow.
- **Network Management Layer (NML):** filters and correlates raw data from multiple elements to identify events (e.g. "area-border router failure").
- **Service Management Layer (SML):** adds intelligence and automation; ties multiple databases together for incident and change management.
- **The Manager of Managers (MoM):** a central tool that provides a final filter and correlates accounting data with performance and inventory databases.

## Challenges in Accounting

- **Caching and multicast fairness:** should users be charged for cache hits or multicast streams? The underlying debate: is the organisation charging for a "data product" or a "connectivity service"?
- **International vs domestic traffic:** commercial links often have varied rates; differentiating national IP ranges requires complex routing table analysis.
- **Remote survivability (NOC operations):** if a Network Operations Center (NOC) is evacuated, accounting functions must remain operational via remote access (KVM over IP, SSH, VPN). Automated protocols (RADIUS/TACACS+) ensure logging continues without human presence.

## Key Takeaways

Without accounting, a network operates in the dark. Rigorous accounting is the science required for security boundaries, capacity forecasting, and organisational transparency.
