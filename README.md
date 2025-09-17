# M4T (Messenger for Tinkerers)
> A silly little pet project made to make use of the interconnectivity of computers in computer labs.

M4T is a lightweight, terminal-based, experimental chat program built on top of **UDP**.  

At its core, M4T is a **two-participant peer-to-peer chat application**:  
- It can send messages to any valid port and address.  
- It can receive messages on a user-specified port.  

Because it relies on UDP, **message delivery is not guaranteed** - packets may be dropped, duplicated, or arrive out of order.  

Written on Java 11.

---

## Current Limitations
- Tested only in local area networks (LAN).  
- May require manual port forwarding for use across networks.  
- Carrier-grade NAT may block peer-to-peer communication unless IPv6 is used.  
- No encryption - all communication is plaintext.  
- No message authentication - any packet sent to the target port will be displayed.  

---

# General Payload and Message Format
## Payload Structure
- **Message ID:**  
  - 16-bit signed integer (`short`, Java).  
  - Encoded in **big-endian (network byte order)**.  
  - Used to identify retransmissions of the same message.  
  - Must be retained in memory for up to **30 seconds** or **64 unique IDs**, whichever comes first.  
  - Discard after expiry.
- **Message Content:**  
  - UTF-8 encoded string.  
  - Zero-length messages are **discarded** and must not be processed.  
  - Entire payload (`ID + content`) must not exceed **800 bytes**, unless peers negotiate another limit.
## Reserved Prefixes
- `"."` → **Client commands**.  
  - Must not be transmitted.  
  - If received, discard/ignore.  
- `"/"` → **Server/hub commands** (optional, reserved for higher-level handling).  
- `"|^~"` → **Semaphores (internal signals)**.  
  - Control program behaviour.  
  - Must never be shown in user output.
## Acknowledgements
- **All non-reserved messages** (displayable text) must be acknowledged.  
- ACK format: `|^~ACK <acknowledgedMsgId> <acknowledgedMsg>`
- `<acknowledgedMsgId>` → numeric string of the signed short ID.  
- `<acknowledgedMsg>` → original UTF-8 message content.  
- ACKs **must never themselves trigger further ACKs**.
---

# Roadmap
- [x] Implement a hub/relay program to support multiple clients in a server-mediated chat.  
- [ ] Add a contract system providing:  
  - [ ] Message acknowledgements  
  - [ ] Encryption  
  - [ ] Message source filtering  

---

# Contributing
Contributions are welcome. Please preserve the "godclass" nature of M4TChatProgram and M4TChatHub.

1. Fork the repository.  
2. Create a feature branch for your changes.  
3. Open a pull request against `main`, ideally with a clear and focused commit history.  
