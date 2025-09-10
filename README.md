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

## Message Format
- All payloads are considered user messages unless reserved.  
- Messages beginning with a **dot (`.`)** are reserved for client commands and should not be transmitted. Incoming messages starting with a dot are discarded.  
- Messages beginning with a **slash (`/`)** may be reserved for command handling by hubs/servers.  
- Messages beginning with **`|^~`** are treated as internal semaphores. These trigger program behaviour and are not shown in user output.  

---

## Roadmap
- [ ] Implement a hub/relay program to support multiple clients in a server-mediated chat.  
- [ ] Add a contract system providing:  
  - Message acknowledgements  
  - Encryption  
  - Message source filtering  

---

## Contributing
Contributions are welcome.  

1. Fork the repository.  
2. Create a feature branch for your changes.  
3. Open a pull request against `main`, ideally with a clear and focused commit history.  
