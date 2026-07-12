#!/usr/bin/env bash
# start-fabric.sh - Start the Hyperledger Fabric test network from WSL
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEST_NET_DIR="$SCRIPT_DIR/Blockchain Network/fabric-samples/test-network"
BIN_DIR="$SCRIPT_DIR/Blockchain Network/fabric-samples/bin"

export PATH="$BIN_DIR:$PATH"
export FABRIC_CFG_PATH="$SCRIPT_DIR/Blockchain Network/fabric-samples/config"

echo "======================================="
echo " Votetrust Ledger - Fabric Network"
echo "======================================="
echo ""
echo "Working directory: $TEST_NET_DIR"
echo "Fabric bin path:   $BIN_DIR"
echo ""

cd "$TEST_NET_DIR"

echo "[1/2] Starting network and creating channel 'mychannel'..."
./network.sh up createChannel -c mychannel -ca

echo ""
echo "[2/2] Done! Fabric network is up."
echo "  Orderer:  localhost:7050"
echo "  Peer Org1: localhost:7051"
echo "  Peer Org2: localhost:9051"
echo ""
echo "The fabric_test Docker network is now available for core-gateway."
