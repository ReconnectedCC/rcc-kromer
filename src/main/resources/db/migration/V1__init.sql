CREATE TABLE IF NOT EXISTS wallets (
    address TEXT PRIMARY KEY,
    uuid TEXT,
    privatekey TEXT,
    outgoingNotSeen TEXT,
    incomingNotSeen TEXT
)