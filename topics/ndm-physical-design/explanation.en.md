# Physical Design — Network Design Process

Physical network design is the process of translating abstract logical architectures (IP schemas, VLANs, protocols) into **tangible infrastructure**. Its objective is to account for real-world constraints, including spatial layout, routing, and environmental requirements. It rests on **three pillars**: cabling infrastructure, hardware placement, and power/environmental controls.

Structured cabling is roughly 5% of network investment but determines 80% of reliability. Following TIA/EIA standards eliminates unplanned downtime and simplifies upgrades.

![Infographic: Structured Cabling & Management — The Backbone of a Reliable Network. Shows the core subsystems of structured cabling within a building cutaway (entrance facility/equipment room, backbone and horizontal cabling, telecommunications closet and work area) alongside best practices for installation and management, and a cable media comparison of UTP, STP, and fibre optic.](images/slide02-structured-cabling-and-management.png)

## Why Rigorous Physical Design Matters

- **Cost impact:** cabling typically accounts for less than 10% of total network cost but outlives most other components (approx. **16+ years**).
- **Reliability:** nearly **70% of network-related problems** are attributed to poor cabling techniques or component failures.
- **Consequences of flawed design:** signal degradation, hardware failure due to overheating, and severe safety/fire hazards.

## Regulatory Framework: Codes vs Standards

- **Codes (the law):** minimum safety requirements (e.g. the National Electrical Code/NEC) adopted by local authorities to protect life and property.
- **Standards (guidelines):** voluntary criteria (e.g. ANSI/TIA-568) issued to ensure interoperability and construction quality across different vendors.
- **Compliance** means the system meets both legal safety codes and performance-based industry standards.

Governing standards organisations:

- **ANSI/TIA/EIA:** defines commercial building telecommunications cabling standards (TIA/EIA-568-A/B).
- **ISO/IEC:** international standards, specifically ISO/IEC 11801, providing global generic cabling specifications.
- **NEC/NFPA:** governs fire safety, heat resistance, and visibility of smoke for cabling materials.

## Structured Cabling Subsystems

Physical design uses a modular framework divided into **six subsystems**:

- **Entrance Facilities (EF):** the service provider interface and demarcation point.
- **Equipment Room (ER):** centralised space for core network/telecom equipment.
- **Backbone Cabling:** inter-building and intra-building connections.
- **Telecommunications Room (TR) / Enclosure (TE):** localised closets housing access layer hardware.
- **Horizontal Cabling:** connections from TRs to work area outlets.
- **Work Area:** end-user device interfaces (faceplates, patch cords).

### Horizontal Cabling Constraints

- **Topology:** must use a star topology for scalability and ease of troubleshooting.
- **Distance limits:** the maximum allowable distance for copper horizontal permanent links is **90 metres**.
- **Channel link:** an additional 10 metres is permitted for patch cords, resulting in a **100-metre total channel link**.

### Backbone (Vertical) Cabling Design

- **Function:** connects the Main Distribution Frame (MDF) to Intermediate Distribution Frames (IDFs) across floors or buildings.
- **Media preference:** optical fibre is the preferred medium due to superior bandwidth, immunity to EMI, and long-distance capabilities.
- **Configuration:** typically implemented as an extended star topology in multi-storey or multi-building campuses.

## Media Selection

| Media | Cost | Capacity | Max Range |
|---|---|---|---|
| UTP (Unshielded) | Lowest | 10 Mbps – 100 Mbps | 100 m |
| STP (Shielded) | Mid | 16 Mbps – 500 Mbps | 500 m |
| Fibre Optic | Highest | 100 Mbps – 200 Gbps | 10s of km |

Selection criteria:

- **Bandwidth & distance:** fibre-optic cable is essential for long-haul ISP links (up to 40 km+) or high-aggregation backbones.
- **Environmental interference:** fibre is immune to Electromagnetic Interference (EMI), making it ideal for industrial settings or hospital environments.
- **Cost vs performance:** twisted-pair copper (Cat 6/6A) remains the standard for horizontal cabling due to cost-effective hardware interfaces.

### Copper Cable Performance Categories

- **Cat 5e:** supports up to 100 MHz; designed for full-duplex Fast Ethernet and limited Gigabit Ethernet.
- **Cat 6:** rated for 250 MHz; supports 10 Gbps up to 55 metres.
- **Cat 6A:** rated for higher performance; supports 10 Gbps up to 100 metres and is ideal for high-power PoE++ deployments.

### Optical Fibre Specifications

- **Multi-mode Fibre (MMF):** uses laser-optimised cores (OM3/OM4); suitable for building backbones up to 550 metres at 10 Gbps.
- **Single-mode Fibre (SMF):** features a small core for straight-line light travel; offers virtually unlimited bandwidth for campus or long-haul WAN links.
- **Safety:** fibre does not conduct electricity, eliminating issues with ground loops or lightning damage between buildings.

## Pathways, Interference, and Cable Integrity

### Pathway and Containment Systems

- **Conduits:** rigid or flexible metallic/non-metallic pipes for physical protection.
- **Cable trays (ladder racks):** overhead wire racks that support high-volume horizontal runs and prevent cable sagging/stress.
- **J-hooks:** economical metal supports for small bundles, typically spaced every 1.2 to 1.5 metres to prevent tension.

### Electromagnetic Interference (EMI) Mitigation

- **Separation:** data cables must be segregated from high-voltage electrical lines (**minimum 5-inch separation**).
- **Intersections:** when crossing power cables, data lines should do so at **right angles** to minimise inductive noise.
- **Sources to avoid:** fluorescent lighting ballasts, motors, generators, and microwave ovens.

### Bend Radius and Tension

- **Bend radius:** strict limits prevent copper pair kinking or micro-fractures in glass fibre — copper: minimum 4× the outer cable diameter; fibre: 10× to 20× the diameter under tension.
- **Pulling tension:** should not exceed 110 N (25 lbs) for 4-pair UTP to avoid stretching conductors and degrading signal timing.

### Fire Safety Ratings (NEC Standards)

- **Plenum (CMP):** required for air-handling spaces; uses fire-resistant, low-smoke materials like Teflon FEP.
- **Riser (CMR):** used for vertical shafts between floors; fire-resistant but does not meet smoke-emission requirements for plenums.
- **Firestopping:** the essential practice of sealing wall/floor penetrations with fire-resistant material to prevent floor-to-floor fire spread.

## Spatial Planning: MDF and IDF

- **Main Distribution Frame (MDF):** the primary hub housing core switches, primary routers, edge firewalls, and incoming ISP circuits. It must be a dedicated, locked room with controlled access, with dedicated HVAC maintaining **18–24°C** and humidity between **30–55%**.
- **Intermediate Distribution Frame (IDF):** localised closets strategically placed to bypass the 100-metre copper distance limitation. Ideally **"stacked" vertically floor-to-floor** to simplify backbone riser cabling, and uplinked back to the MDF via high-speed fibre backbone connections.

## Rack Architecture and Ergonomics

- **Dimensions:** industry standard 19-inch racks or cabinets; vertical space is measured in Rack Units (RU or U), where **1U = 1.75 inches**.
- **Grounding:** all racks must be properly bonded and grounded to the building's main grounding system per ANSI/TIA-607.
- **Weight distribution:** the heaviest assets (UPS, battery packs, modular chassis) must be installed at the absolute bottom to lower the centre of gravity.
- **Patch panel placement:** ideally positioned at eye/chest level for ease of termination and patch cord management.
- **Cable managers:** horizontal and vertical managers (D-rings, finger ducts) prevent blocking hardware exhaust vents.

## Power Design

### Electrical Load Calculations

- **Critical load assessment:** list all IT hardware nameplate power ratings (VA or Watts) and voltage requirements.
- **Adjustments:** nameplate data often represents worst-case scenarios; adjust for anticipated actual load (diversity factor).
- **Safety margin:** continuous loads must not exceed **80% of a circuit's maximum rated capacity** (e.g. 16 A on a 20 A circuit).

### Power Continuity: The UPS

- Provides immediate battery-backed power during utility failure and buffers against "dirty power" (surges, sags, spikes).
- Must bridge the gap until emergency generators start, or allow graceful automated shutdowns of critical systems.
- Must be placed at the bottom of the rack due to its significant weight.

### Power Distribution and Redundancy

- **Intelligent PDUs:** mounted inside racks to distribute power; allow real-time current monitoring and remote power cycling.
- **True resilience:** dual-homed devices should have redundant PSUs plugged into independent PDUs fed by separate UPS systems or utility grids.
- **Surge protection:** essential for all telecommunications closets and entrance facilities.

## Thermal Management

### Hot/Cold Aisle Design

- **The cold aisle:** racks face each other; cold air is pumped from the floor and pulled through front intake fans.
- **The hot aisle:** racks face rear-to-rear; hot air is exhausted out the back, rises, and is captured by return vents.
- **Isolation:** physical barriers (curtains/doors) can further seal aisles to maximise cooling efficiency.

### Optimising Server Room Airflow

- **Blanking panels:** essential for filling empty rack slots to force air through active equipment rather than around it.
- **Clearance:** maintain at least 75 mm (3 inches) of clearance in front of equipment intakes to ensure unobstructed airflow.
- **Efficiency:** proper management reduces HVAC load, lowering operational costs and increasing hardware longevity.

## Planning, Installation, and Verification

### The RFP and Needs Analysis Process

- **Objective:** define current needs while ensuring flexibility for future technologies (moves, adds, changes/MACs).
- **Buy-in:** solicit input from management, facilities, and IT to ensure the infrastructure is a business "enabler".
- **Standardisation:** establish organisational standard outlet configurations to simplify parts ordering and troubleshooting.

### Physical Installation Workflow

- **Scheduling:** coordinate with electrical and building inspectors; ideally install after electrical wiring to ensure segregation.
- **Procedures:** pulling, labelling both ends, and terminating cables using standardised patterns (T568A or T568B).
- **Service loops:** leave slack (e.g. 3 metres in the ceiling, 300 mm at the outlet) to allow future re-termination or equipment movement.

### Testing and Certification

- **Wire-map:** verifies correct pin termination, absence of shorts, and proper pair assignments.
- **Attenuation & NEXT:** measures signal loss and Near-End Crosstalk at specified frequencies to ensure category compliance.
- **Third-party certification:** multifunction scanners provide hard-copy proof that the installation meets TIA performance levels.

### Performance Degradation Factors

- **Crosstalk:** transfer of unwanted signals between pairs; exacerbated by over-tightening cable ties or untwisting pairs too far at termination.
- **Return loss:** signal reflections caused by impedance discontinuities, such as crushed cables or bad connectors.
- **Delay skew:** variations in propagation time across different wire pairs, critical for high-speed protocols like Gigabit Ethernet.

## Documentation and Administration

Installation and management best practices:

- **The power of colour-coding:** blue for data, green for management, red for alert systems.
- **Physical constraints for longevity:** use Velcro straps instead of zip ties to protect cable jackets.
- **Critical documentation essentials:** maintain Layer 1–3 diagrams, circuit tables, and IP allocation databases for troubleshooting.

### Layer 1 & 2 Documentation Best Practices

- **Physical connectivity:** diagrams showing infrastructure links, link speeds, and cable types.
- **Port mapping:** label individual ports on diagrams to match physical numbers on patch panels.
- **Visual standards:** thicker lines for high-speed backbone links and distinct colours to differentiate copper vs fibre.

### Rack and Cable Plan Administration

- **Rack elevations:** meticulously accurate diagrams showing equipment in specific numerical rack positions (U-heights).
- **Labelling (ANSI/TIA-606):** consistent, machine-printed labelling for every cable end, outlet, and patch panel port.
- **Asset tracking:** tables of critical infrastructure (switches, routers, firewalls) and their associated support contracts.

## Physical Design Sign-Off Checklist

- Horizontal copper runs strictly under the 90-metre permanent link threshold?
- Plenum-rated jackets specified for all air-handling pathways?
- Minimum 5-inch separation maintained from high-voltage electrical lines?
- Heaviest assets mapped to the bottom of equipment racks?
- Total circuit load sits below 80% of rated capacity?
- Room layout supports a distinct hot/cold aisle thermal path?
