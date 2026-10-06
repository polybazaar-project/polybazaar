# PolyBazaar

### Pitch
Students always pay full price for things they'll only need once, some tools, an interview suit, because there's no easy way to find that item or skill around them. 

This hits hardest for students living away from home for the first time, without their own tools, or social circle to help them. 

PolyBazaar solves this with a peer-to-peer marketplace for verified student: rent out things you own or list a skill you're good at, then browse, chat, book, and review it all in one app. 

The insight is simple: a campus already has a trust-friendly community sitting on idle stuff and talent, it just needs a connective layer. 

Built for students like Maya, a PhD student who'd rent out her 3D printer between projects, and James, a freshman who needs a suit for one interview and help with an algebra problem set, and would rather pay a fellow student than a store or an agency.

### Split-app model
Backend server (Firebase) handles storing accounts, listings, and transactions, with cloud storage holding item images. A maps/geolocation API (Google Map) powers the live map of nearby tools and services, and a push notification service delivers chat and booking alerts to the mobile app.

### Multi-user support
Users sign up and log in with a username/password, verifying their university email as well. Each account is one profile that can act as both lender and renter, with the server tracking listings, transactions, and verification status across devices. We'll let the payment be handled by the users. In case of damaged, the lender define before hand an amount needed to be paid to compensate the broken item.

### Sensor use
GPS powers the live map of nearby tools and specialized services. The camera is used to photograph items for listings.

### Offline use
Users can access their toolbox (owned and currently rented items), profile details, and transaction history, without connection. Users can also draft posts, messages, and change their profile, the changes will be synced when connection is retrieved.

### Firebase security rules
Firestore and Storage rules are versioned in `firestore.rules` and `storage.rules`. On pushes to
`main`, CI deploys them with `FIREBASE_PROJECT_ID` and `FIREBASE_SERVICE_ACCOUNT` repository
secrets. The service account must have the Firebase Rules Admin role.