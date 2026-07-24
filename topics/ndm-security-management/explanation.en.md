# Security Management — Network Management

Security Management ("S" in **FCAPS**) is defined by ISO as the process of controlling access to network resources to prevent intentional or unintentional sabotage and unauthorised access to sensitive data. It serves as the network's **"immune system"**, involving a continuous cycle of risk identification, safeguard implementation, traffic monitoring, and incident response.

![Infographic: Mastering Network Security Management — Frameworks and Defence. The left side shows core frameworks and security objectives (FCAPS model, CIA Triad, AAA gatekeeper); the right side covers technical defence and monitoring (SNMPv3, NIDS vs. HIDS, layered defence-in-depth, and SNMP Traps vs. Informs).](images/slide02-frameworks-and-defence.png)

## The CIA Triad: The Core Security Objectives

- **Confidentiality:** shielding sensitive data from unauthorised entities using encryption and **Role-Based Access Control (RBAC)**.
- **Integrity:** ensuring data remains accurate and unaltered during transit or storage via cryptographic hash functions (e.g. **SHA-256**).
- **Availability:** providing authorised users uninterrupted access to network assets through hardware redundancy and **DDoS mitigation**.

## Defence-in-Depth

A layered architectural approach where **multiple redundant security controls** are engineered to intercept threats if one layer fails. It extends from the network perimeter to individual host agents and data-level encryption — utilising firewalls, network segmentation, and encryption ensures that if one layer falls, others remain.

## The AAA Framework: The Network Gatekeeper

- **Authentication:** verifying user identity through credentials, biometrics, or **Multi-Factor Authentication (MFA)**.
- **Authorisation:** determining exactly what an authenticated user is permitted to do based on rights and roles.
- **Accounting:** creating a **chronological audit trail** of all actions performed during an active session.

### Centralised AAA Management

- Dedicated servers (e.g. **TACACS+**, **RADIUS**, or **LDAP**) consolidate administrative access and improve consistency across thousands of devices.
- Encrypted transport for AAA protocols (e.g. **RadSec** or **LDAPS**) prevents credential sniffing.

### The Principle of Least Privilege

- Authorise users at the **lowest privilege level necessary** to perform their tasks, minimising the impact of a compromised account.
- Use separate credentials or **"enable" commands** to elevate to privileged levels for specific tasks.

## Network Perimeter Defence

### Firewalls

Firewalls act as a barrier between trusted internal and untrusted external networks:

- **Packet-Filtering:** inspects traffic at **Layers 3 and 4** (IP and Port) for speed and efficiency.
- **Next-Generation Firewalls (NGFW):** perform **Deep Packet Inspection (DPI) at Layer 7** to identify specific applications.

### Demilitarised Zone (DMZ) Architecture

- A **buffer zone** between production networks and untrusted networks for publicly accessible servers (Web, Email, FTP).
- **Dual-firewall layers from different vendors** protect against unpatched vulnerabilities exploited in a single vendor's software.

## Segmentation and Layer 2 Hardening

- **Logical and physical segmentation:** dividing a network into isolated **Virtual Local Area Networks (VLANs)** prevents **lateral movement** of malware. Similar systems (e.g. IoT, Finance, Guest Wi-Fi) are grouped, with strict **Access Control Lists (ACLs)** applied between segments.
- **Port Security:** limits the number of valid MAC addresses per switchport to prevent unauthorised rogue devices. Configure **"sticky" MAC addresses** and ensure violation modes (e.g. shutdown) are active to block spoofing attempts.
- **802.1X and Network Access Control (NAC):** a robust solution for authenticating devices based on **trusted digital certificates** before granting network access — a higher level of assurance and manageability than simple MAC filtering.

## Intrusion Detection and Prevention

### Intrusion Detection Systems (IDS)

- **Passive monitoring** tools that analyse copies of traffic against databases of known attack signatures.
- **HIDS (Host-based):** monitors activity on a local host (logs, processes, user activity).
- **NIDS (Network-based):** monitors all traffic traversing a network segment to identify possible intrusions.

### Intrusion Prevention Systems (IPS)

- **In-line placement** allows the system to actively **drop dangerous packets or reset connections in real time**.
- Requires careful tuning to avoid **"False Positives"** — legitimate traffic mistakenly reported as malicious.

### Detection Methodologies: Signature vs Anomaly

- **Signature-based:** uses explicit patterns (e.g. looking for the string "root" or specific byte sequences).
- **Anomaly-based:** defines a "normal" traffic baseline and alerts on deviations (e.g. sudden spikes in RPC sessions).

### Automated Alarm Validation and Response

- Modern security management uses automated systems to assess whether a detected attack was actually successful by checking the target's OS and patch level — reducing administrator **"triage time" from minutes to seconds**.
- Responding to intrusions: immediate triage and containment, followed by a **mandatory root cause analysis**; security policies and signatures are updated based on the findings to prevent future occurrences.

## Securing Management Traffic

### SNMP Security

- **Vulnerabilities of v1 and v2c:** these versions use **"community strings" sent in plain text**, which are easily intercepted.
- **SNMPv3 USM (User-based Security Model):** implements authentication (MD5/SHA) and privacy/encryption (DES/AES) to protect management data.
- **SNMPv3 VACM (View-based Access Control Model):** controls access to specific **MIB (Management Information Base)** objects by configuring different views for different user groups — an administrator can read/write the entire tree while a standard user might only view system status.

### SNMP Traps vs Informs

| | SNMP Traps | SNMP Informs |
|---|---|---|
| **Reliability** | Sent only once; no acknowledgement | Retried until an acknowledgement is received |
| **Resources** | Discarded as soon as sent, low consumption | Held in memory until response or timeout |
| **Security** | Insecure plain-text (v1/v2c) | Secure with authentication/privacy (v3) |

### Hardening Remote Administration

- **Disable clear-text protocols** (Telnet, HTTP, FTP, SNMP v1/2c) in favour of encrypted alternatives like **SSH and HTTPS**.
- Enforce **SSH version 2** and strong asymmetric keys (e.g. **3072-bit RSA or 384-bit ECC**).

## Data Protection

- **Data-in-transit (VPNs):** **IPsec or TLS tunnels** protect data traversing untrusted pathways; enforce high-strength cryptography for VPN policies, such as **AES-256** for encryption and **SHA-384** for hashing.
- **Data-at-rest:** encrypt hard drives and databases where sensitive files and configurations are stored; protect device configuration backups using encrypted protocols like **SFTP or SCP**.

## Logging, Time, and Forensics

- **Log management:** collect logs (Application, Security, System) into **centralised remote servers** to prevent erasure by an attacker; analyse logs for correlation to find lateral movement across geographically dispersed networks.
- **Time synchronisation:** use **Network Time Protocol (NTP) with authentication** to ensure accurate timestamps across all network devices — accurate clocks are critical for tracing incidents chronologically across different logs.

## Audits, Risk Assessment, and Pentesting

- **Security audits:** systematically review systems to identify vulnerabilities and ensure compliance with standards like **NIST or CNSSP**.
- **Risk assessment:** assess the likelihood and impact of potential threats to prioritise security investments.
- **Attack simulation and pentesting:** simulate attacker behaviour (e.g. footprinting, SQL injection, DoS floods) using tools like **Kali Linux** to find weaknesses before attackers do. Acting as both attacker and defender helps refine security strategies and rule sets for firewalls and IDS.
- **Social engineering and internal threats:** train employees to recognise phishing and other social engineering attacks that bypass technical controls; monitor for insider misuse through robust **IAM (Identity and Access Management)** systems.

## Future Directions

- **Zero Trust Architecture:** a shift away from traditional perimeter boundaries toward a model that **assumes no entity is trusted by default**; requires continuous verification of every access request through strict authentication and **micro-segmentation**.
- **AI and Machine Learning:** leveraged to analyse massive volumes of traffic in real time to identify anomalies that signal sophisticated breaches; enables **"self-healing" networks** that can automatically isolate threats.
- **Automation and orchestration:** automating routine tasks like vulnerability scanning and patch management reduces the burden on IT teams and speeds up decision-making and response times during active incidents.

## Key Takeaways

- Security Management is a **continuous, evolving cycle**, not a one-time setup.
- A perpetual design tension: balancing high-friction security measures (e.g. 30-minute MFA timeouts) with user productivity and the CIA Triad.
