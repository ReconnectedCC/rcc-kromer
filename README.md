# rcc-kromer [![Build Java](https://github.com/ReconnectedCC/rcc-kromer/actions/workflows/build.yml/badge.svg?branch=master)](https://github.com/ReconnectedCC/rcc-kromer/actions/workflows/build.yml)
### ReconnectedCC's economy mod.
This version of rcc-kromer uses JKromer, which can be found [here](https://github.com/ReconnectedCC/rcc-kromer).

## Supports
- Viewing balance via /balance,
- Viewing your stored private key & address via /kromer info
- Welfare, and welfare opting in/out
- Transaction viewing
- Payments & payment confirmations

The database to store all of this is twofold, for user toggles I use Solstice's PlayerData, for private keys and addresses I use Flyway + SQLite.

## License
As of currently, All Rights Resered.