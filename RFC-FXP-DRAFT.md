# RFC Draft: Secure FXP Implementation for DrFTPD

**Draft Status**: Initial Proposal
**Date**: 2026-01-14
**Feature Branch**: `draft/fxp-rfc`

## 1. Abstract
This document outlines a proposal to standardize and enhance the security of File eXchange Protocol (FXP) support in DrFTPD. While DrFTPD currently supports FXP via `PassiveConnection` and `ActiveConnection`, there is no mechanism to enforce mutual authentication or strictly validate the identity of the participating slaves, leaving a potential gap compared to modern secure FTP standards like SSCN (Set Secure Command Negotiation).

## 2. Current Implementation
DrFTPD handles FXP by allowing one Slave to act as a passive listener (PASV) and instructing another Slave to connect to it (PORT/EPRT).
*   **Signaling**: The Master coordinates the transfer by sending `PASV` to the source and `PORT` to the destination (or vice versa).
*   **Data Connection**: The slaves connect directly using standard SSL/TLS if configured.
*   **Security Gap**: The data connection does not verify that the connecting peer is actually the intended slave. Since `slavemanager.ssl.clientauth` (introduced in Issue #138) only applies to the Master-Slave control connection, the Slave-Slave data connection remains vulnerable to "Confused Deputy" attacks if an attacker can hijack the IP or port.

## 3. Threat Model
*   **Man-in-the-Middle (MitM)**: Without mutual authentication on the data channel, an attacker could potentially intercept or inject data between slaves.
*   **Unauthorized Access**: A malicious actor could theoretically connect to a passive slave's open port if they know the IP/port, although the window is small.

## 4. Proposed Extensions

### 4.1. SSCN (Set Secure Command Negotiation) support
Implement the `SSCN` command (RFC draft extension) or equivalent logic.
*   **Mechanism**: Before initiating the data connection, slaves exchange or verify identity.
*   **Implementation**: Slaves could reuse the certificate fingerprints (from Issue #138) to validate each other.
    *   Master sends "Expected Peer Fingerprint" to both slaves.
    *   Slaves enforce that the connecting peer presents a certificate matching this fingerprint.

### 4.2. CPSV (Client PASV)
Enhance NAT traversal by supporting CPSV-style negotiation where the Master acts as a proxy or smarter coordinator for NATed slaves.

### 4.3. IPv6 Support (EPRT/EPSV)
Modernize the protocol to support IPv6 addresses for both Control and Data connections.
*   **Protocol Updates**: Implement `EPRT` (Extended PORT) and `EPSV` (Extended PASV) commands (RFC 2428).
*   **DrFTPD Impact**:
    *   Update `Master.java` and `SlaveManager.java` to handle IPv6 address parsing.
    *   Update `PassiveConnection` and `ActiveConnection` to bind/connect using `Inet6Address`.
    *   Ensure HostMask checks support CIDR notification for IPv6 (e.g., `2001:db8::/32`).

## 5. Implementation Roadmap
1.  **Phase 1**: Research current `javax.net.ssl` capabilities for dynamic trust management (to trust specific peers per-transfer).
2.  **Phase 2**: Extend `Transfer` object to include "Peer Fingerprint" metadata.
3.  **Phase 3**: Update `PassiveConnection` and `ActiveConnection` to accept an optional `verifiedPeerFingerprint`.

## 6. Feedback Requested
*   Is strict SSCN compliance required, or is a custom "Fingerprint-locked FXP" preferred?
*   Should this be enforced globally or per-transfer?
