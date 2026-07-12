package main

import (
	"encoding/json"
	"fmt"
	"time"

	"github.com/hyperledger/fabric-contract-api-go/contractapi"
)

type VotingContract struct {
	contractapi.Contract
}

type Election struct {
	ElectionID   string `json:"electionId"`
	Name         string `json:"name"`
	IsOpen       bool   `json:"isOpen"`
	CreationTime int64  `json:"creationTime"`
}

type Ballot struct {
	BallotToken string `json:"ballotToken"`
	ElectionID  string `json:"electionId"`
	CandidateID string `json:"candidateId"`
	Timestamp   int64  `json:"timestamp"`
}

func (c *VotingContract) InitLedger(ctx contractapi.TransactionContextInterface) error {
	defaultElection := Election{
		ElectionID:   "elect-2026",
		Name:         "General Presidential Sandbox Election",
		IsOpen:       true,
		CreationTime: time.Now().Unix(),
	}
	electionBytes, err := json.Marshal(defaultElection)
	if err != nil {
		return fmt.Errorf("failed to marshal default election: %v", err)
	}
	return ctx.GetStub().PutState("ELECTION_"+defaultElection.ElectionID, electionBytes)
}

func (c *VotingContract) CreateElection(ctx contractapi.TransactionContextInterface, electionId string, name string) error {
	exists, err := c.AssetExists(ctx, "ELECTION_"+electionId)
	if err != nil {
		return err
	}
	if exists {
		return fmt.Errorf("the election %s already exists", electionId)
	}
	election := Election{
		ElectionID:   electionId,
		Name:         name,
		IsOpen:       true,
		CreationTime: time.Now().Unix(),
	}
	electionBytes, err := json.Marshal(election)
	if err != nil {
		return err
	}
	return ctx.GetStub().PutState("ELECTION_"+electionId, electionBytes)
}

func (c *VotingContract) CastVote(ctx contractapi.TransactionContextInterface, ballotToken string, electionId string, candidateId string) error {
	electionBytes, err := ctx.GetStub().GetState("ELECTION_" + electionId)
	if err != nil {
		return fmt.Errorf("failed to read from world state: %v", err)
	}
	if electionBytes == nil {
		return fmt.Errorf("the election %s does not exist", electionId)
	}
	var election Election
	err = json.Unmarshal(electionBytes, &election)
	if err != nil {
		return err
	}
	if !election.IsOpen {
		return fmt.Errorf("voting window closed for election %s", electionId)
	}
	ballotKey := "BALLOT_" + ballotToken
	ballotExists, err := c.AssetExists(ctx, ballotKey)
	if err != nil {
		return err
	}
	if ballotExists {
		return fmt.Errorf("security breach token reuse alert: ballot token %s has already been spent", ballotToken)
	}
	ballot := Ballot{
		BallotToken: ballotToken,
		ElectionID:  electionId,
		CandidateID: candidateId,
		Timestamp:   time.Now().Unix(),
	}
	ballotBytes, err := json.Marshal(ballot)
	if err != nil {
		return err
	}
	return ctx.GetStub().PutState(ballotKey, ballotBytes)
}

func (c *VotingContract) AggregateTally(ctx contractapi.TransactionContextInterface, electionId string) (map[string]int, error) {
	startKey := "BALLOT_"
	endKey := "BALLOT_z"
	resultsIterator, err := ctx.GetStub().GetStateByRange(startKey, endKey)
	if err != nil {
		return nil, err
	}
	defer resultsIterator.Close()
	tallyMap := make(map[string]int)
	for resultsIterator.HasNext() {
		queryResponse, err := resultsIterator.Next()
		if err != nil {
			return nil, err
		}
		var ballot Ballot
		err = json.Unmarshal(queryResponse.Value, &ballot)
		if err != nil {
			return nil, err
		}
		if ballot.ElectionID == electionId {
			tallyMap[ballot.CandidateID]++
		}
	}
	return tallyMap, nil
}

func (c *VotingContract) AssetExists(ctx contractapi.TransactionContextInterface, key string) (bool, error) {
	assetBytes, err := ctx.GetStub().GetState(key)
	if err != nil {
		return false, fmt.Errorf("failed to read world state reference for key %s: %v", key, err)
	}
	return assetBytes != nil, nil
}

func main() {
	cc, err := contractapi.NewChaincode(&VotingContract{})
	if err != nil {
		panic(fmt.Sprintf("Error creating voting system chaincode module: %v", err))
	}
	if err := cc.Start(); err != nil {
		panic(fmt.Sprintf("Error starting voting system chaincode service: %v", err))
	}
}
