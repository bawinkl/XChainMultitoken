/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bawinkl.score.xchainmultitoken;


import score.ByteArrayObjectWriter;
import score.Context;
import score.DictDB;
import score.ArrayDB;
import score.VarDB;
import score.BranchDB;
import score.Address;

import java.util.Map;

import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;

import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.Json;

import com.bawinkl.score.xchainmultitoken.sdos.*;
import com.iconloop.score.token.irc31.IRC31;

public class XChainMultiToken implements IRC31 {

    // ================================================
    // Consts
    // ================================================
    public static final Address ZERO_ADDRESS = new Address(new byte[Address.LENGTH]);

    // ================================================
    // SCORE DB
    // ================================================
    // id => (owner[NetworkAddress String] => balance)
    private final BranchDB<BigInteger, DictDB<String, BigInteger>> bdbBalances = Context
            .newBranchDB("balances", BigInteger.class);
    // owner[NetworkAddress String] => (operator[NetworkAddress String] => approved)
    private final BranchDB<String, DictDB<String, Boolean>> bdbOperatorApproval = Context
            .newBranchDB("approval", Boolean.class);
    // id => token URI
    private final DictDB<BigInteger, String> dbTokenURI = Context.newDictDB("token_uri", String.class);
    // The networkID for this SCORE
    public final VarDB<String> varNetworkID = Context.newVarDB("network_id", String.class);
    // The XCall contract endpoint
    public final VarDB<Address> varXCallContract = Context.newVarDB("xcall_contract", Address.class);
    // id ==> creator
    private final DictDB<BigInteger, NetworkAddress> dbCreators = Context.newDictDB("creators", NetworkAddress.class);


    public XChainMultiToken(boolean _update) {
        if (_update) {
            onUpdate();
        } else {
            onInstall();
        }
    }

    public void onUpdate() {
    }

    public void onInstall() {
    }

    // ================================================
    // XCall Contract Management
    // ================================================

    /**
     * Get the configured XCall Contract
     */
    @External(readonly = true)
    public Address getXCallContract() {
        return varXCallContract.getOrDefault(ZERO_ADDRESS);
    }

    /**
     * Sets the network ID for this score
     * Required to support all local Network Address references
     * Can only be set by the SCORE owner
     * Must be a contract address
     * 
     * @param _value: the contract address
     */
    @External
    public void setXCallContract(Address _value) {
        onlyOwner();
        Context.require(_value.isContract(), "_value must be a contract address.");
        varXCallContract.set(_value);
    }

    // ================================================
    // Network ID Management
    // ================================================

    /**
     * Get the configured network ID for this SCORE
     */
    @External(readonly = true)
    public String getNetworkID() {
        return varNetworkID.getOrDefault("");
    }

    /**
     * Sets the network ID for this score
     * Required to support all local Network Address references
     * Can only be set by the SCORE owner
     * 
     * @param _value: the network ID value
     */
    @External
    public void setNetworkID(String _value) {
        onlyOwner();
        varNetworkID.set(_value);
    }

    

    // ================================================
    // IRC-3 Methods
    // NOTE: All of these have been modified to use the NetworkAddress object for db
    // tracking data in place of "Address" however the original IRC-31 interface requirements remain intact.
    // ================================================

    /**
     * This is the original IRC-31 implementation of balanceOf, which looks up the newly implemented NetworkAddress based on the configured network id and the _owner.
     * Required to match the existing IRC-31 interface requirements
     * @param _owner: the ICON address of the owner
     * @param _id: the token ID
     */
    @External(readonly = true)
    public BigInteger balanceOf(Address _owner, BigInteger _id) {
        Context.require(!varNetworkID.getOrDefault("").isEmpty(), "The Network ID is not configured for this SCORE");
        NetworkAddress ownerAddress = new NetworkAddress(_owner, varNetworkID.get());
        return _balanceOf(ownerAddress, _id);
    }

    /**
     * A XCall compatible implementation of the balanceOf function
     * Returns the balance of a given address
     * This implementation can accept any address, network address or btp address in string format
     * @param _owner: the targeted address in one of the following formats: icon address, network address ([NetworkID]/[Address]) or btp address ([btp://][NetworkID]/[Address])
     * @param _id: the token ID
     */
    @External(readonly = true)
    public BigInteger x_balanceOf(String _owner, BigInteger _id) {
        Context.require(!varNetworkID.getOrDefault("").isEmpty(), "The Network ID is not configured for this SCORE");
        NetworkAddress ownerAddress = new NetworkAddress(_owner, varNetworkID.get());
        return _balanceOf(ownerAddress, _id);
    }

    private BigInteger _balanceOf(NetworkAddress _owner, BigInteger _id) {
        return bdbBalances.at(_id).getOrDefault(_owner.toString(), BigInteger.ZERO);
    }

    /**
     * This is the original IRC-31 implementation of balanceOfBatch, which looks up the newly implemented NetworkAddress based on the configured Network ID and the _owners.
     * Required to match the existing IRC-31 interface requirements
     * @param _owner: and array of ICON addresses
     * @param _id: an array of token IDs
     */
    @External(readonly = true)
    public BigInteger[] balanceOfBatch(Address[] _owners, BigInteger[] _ids) {
        Context.require(!varNetworkID.getOrDefault("").isEmpty(), "The Network ID is not configured for this SCORE");
        NetworkAddress[] ownerNetworkAddresses = new NetworkAddress[_owners.length];
        for (int i = 0; i < _owners.length; i++) {
            ownerNetworkAddresses[i] = new NetworkAddress(_owners[i], varNetworkID.get());
        }
        return _balanceOfBatch(ownerNetworkAddresses, _ids);
    }

    /**
     * A XCall compatible implementation of the balanceOfBatch function
     * This implementation can accept any address, network address or btp address in string format
     * Returns the balances of a set of owners and ids
     * @param _owners: an array of addresses in string format, network address format ([NetworkID]/[Address]) or btp address format ([btp://][NetworkID]/[Address])
     * @param _id: an array of token IDs
     */

    @External(readonly = true)
    public BigInteger[] x_balanceOfBatch(String[] _owners, BigInteger[] _ids) {
        Context.require(!varNetworkID.getOrDefault("").isEmpty(), "The Network ID is not configured for this SCORE");
        NetworkAddress[] ownerNetworkAddresses = new NetworkAddress[_owners.length];
        for (int i = 0; i < _owners.length; i++) {
            ownerNetworkAddresses[i] = new NetworkAddress(_owners[i], varNetworkID.get());
        }
        return _balanceOfBatch(ownerNetworkAddresses, _ids);
    }

    private BigInteger[] _balanceOfBatch(NetworkAddress[] _owners, BigInteger[] _ids) {
        Context.require(_owners.length == _ids.length,
                "_owners array size must match with _ids array size");

        BigInteger[] balances = new BigInteger[_owners.length];
        for (int i = 0; i < _owners.length; i++) {
            balances[i] = _balanceOf(_owners[i], _ids[i]);
        }
        return balances;
    }

    @External(readonly = true)
    public String tokenURI(BigInteger _id) {
        return dbTokenURI.get(_id);
    }

    /**
     * This is the original IRC-31 implementation of transferFrom, which looks up the newly implemented NetworkAddress based on the configured Network ID and the _from, _to and caller.
     * Required to match the IRC-31 interface requirements
     * @param _from: an ICON Address of the tokens to be transferred from
     * @param _to: an ICON Address of the tokens to be transferred to
     * @param _id: the token Id to transfer
     * @param _value: the amount of tokens to transfer
     * @param _data  Additional data that should be sent unaltered in call to {@code _to}
     */
    @External
    public void transferFrom(Address _from, Address _to, BigInteger _id, BigInteger _value, @Optional byte[] _data) {
        Context.require(!varNetworkID.getOrDefault("").isEmpty(), "The Network ID is not configured for this SCORE");
        NetworkAddress fromAddress = new NetworkAddress(_from, varNetworkID.get());
        NetworkAddress toAddress = new NetworkAddress(_to, varNetworkID.get());
        NetworkAddress callerAddress = new NetworkAddress(Context.getCaller(), varNetworkID.get());
        _transferFrom(callerAddress, fromAddress, toAddress, _id, _value, _data);
    }

    /**
     A XCall compatible implementation of the transferFrom function
     * This implementation can accept any address, network address or btp address in string format
     * @param _from: an address in one of the following formats: an ICON address in string format, a network address ([NetworkID]/[Address]) or btp address ([btp://][NetworkID]/[Address])
     * @param _to: an address in one of the following formats: an ICON address in string format, a network address ([NetworkID]/[Address]) or btp address ([btp://][NetworkID]/[Address])
     * @param _id: the token id to transfer
     * @param _value: the amount of tokens to transfer
     * @param _data  additional data that should be sent unaltered in call to {@code _to}
     */
    @External
    public void x_transferFrom(String _from, String _to, BigInteger _id, BigInteger _value, @Optional byte[] _data) {
        Context.require(!varNetworkID.getOrDefault("").isEmpty(), "The Network ID is not configured for this SCORE");
        NetworkAddress fromAddress = new NetworkAddress(_from, varNetworkID.get());
        NetworkAddress toAddress = new NetworkAddress(_to, varNetworkID.get());
        NetworkAddress callerAddress = new NetworkAddress(Context.getCaller(), varNetworkID.get());
        _transferFrom(callerAddress, fromAddress, toAddress, _id, _value, _data);
    }

    private void _transferFrom(NetworkAddress _caller, NetworkAddress _from, NetworkAddress _to, BigInteger _id,
            BigInteger _value, @Optional byte[] _data) {

        Context.require(!_to.getAddress().equals(ZERO_ADDRESS.toString()),
                "_to must be non-zero address");
                
        Context.require(_from.equals(_caller) || _isApprovedForAll(_from, _caller),
                "Need operator approval for 3rd party transfers");
                
        Context.require(_value.signum() >= 0 && _balanceOf(_from, _id).compareTo(_value) >= 0,
                "Insufficient funds to transfer " + _value);

        // Transfer funds
        DictDB<String, BigInteger> balance = bdbBalances.at(_id);
        balance.set(_from.toString(), _balanceOf(_from, _id).subtract(_value));
        balance.set(_to.toString(), _balanceOf(_to, _id).add(_value));

        // Emit event
        x_TransferSingle(_caller.toString(), _from.toString(), _to.toString(), _id, _value);

        // Try to call onIRC31Received
        // this will only work for local network contract addresses, so we wrap it in a
        // try/catch
        try {
            Address toAddress = Address.fromString(_to.getAddress());
            if (toAddress.isContract()) {
                // Call {@code onIRC31Received} if the recipient is a contract    
                Context.call(Address.fromString(_to.getAddress()), "onIRC31Received", Address.fromString(_caller.getAddress()), Address.fromString(_from.getAddress()), _id, _value,
                        _data == null ? new byte[] {} : _data);
            }
        } catch (Exception ex) {
        }
    }

    /**
     * This is the original IRC-31 implementation of transferFromBatch, which looks up the newly implemented NetworkAddress based on the configured Network ID and the _from, _to and caller.
     * Required to match the IRC-31 interface requirements
     * @param _from: an ICON Address of the tokens to be transferred from
     * @param _to: an ICON Address of the tokens to be transferred to
     * @param _ids: an array of token IDs to transfer
     * @param _value: an array of values to transfer (must match the length of _ids, each index in _ids corresponds to the values in this array)
     * @param _data  Additional data that should be sent unaltered in call to {@code _to}
     */
    @External
    public void transferFromBatch(Address _from, Address _to, BigInteger[] _ids, BigInteger[] _values,
            @Optional byte[] _data) {
        Context.require(!varNetworkID.getOrDefault("").isEmpty(), "The Network ID is not configured for this SCORE");
        NetworkAddress callerAddress = new NetworkAddress(Context.getCaller(), varNetworkID.get());
        NetworkAddress fromAddress = new NetworkAddress(_from, varNetworkID.get());
        NetworkAddress toAddress = new NetworkAddress(_to, varNetworkID.get());

        _transferFromBatch(callerAddress, fromAddress, toAddress, _ids, _values, _data);
    }

    /**
     * A XCall compatible implementation of the transferFromBatch function
     * This implementation can accept any address, network address or btp address in string format
     * @param _from: an address in one of the following formats: an ICON address in string format, a network address ([NetworkID]/[Address]) or btp address ([btp://][NetworkID]/[Address])
     * @param _to: an address in one of the following formats: an ICON address in string format, a network address ([NetworkID]/[Address]) or btp address ([btp://][NetworkID]/[Address])
     * @param _ids: an array of token IDs to transfer
     * @param _value: an array of values to transfer (must match the length of _ids, each index in _ids corresponds to the values in this array)
     * @param _data  additional data that should be sent unaltered in call to {@code _to}
     */
    @External
    public void x_transferFromBatch(String _from, String _to, BigInteger[] _ids, BigInteger[] _values,
            @Optional byte[] _data) {
        Context.require(!varNetworkID.getOrDefault("").isEmpty(), "The Network ID is not configured for this SCORE");
        NetworkAddress callerAddress = new NetworkAddress(Context.getCaller(), varNetworkID.get());
        NetworkAddress fromAddress = new NetworkAddress(_from, varNetworkID.get());
        NetworkAddress toAddress = new NetworkAddress(_to, varNetworkID.get());

        _transferFromBatch(callerAddress, fromAddress, toAddress, _ids, _values, _data);
    }

    public void _transferFromBatch(NetworkAddress _caller, NetworkAddress _from, NetworkAddress _to, BigInteger[] _ids,
            BigInteger[] _values, @Optional byte[] _data) {
        Context.require(!_to.getAddress().equals(ZERO_ADDRESS.toString()),
                "_to must be non-zero");
        Context.require(_from.equals(_caller) || _isApprovedForAll(_from, _caller),
                "Need operator approval for 3rd party transfers");

        for (int i = 0; i < _ids.length; i++) {
            BigInteger _id = _ids[i];
            BigInteger _value = _values[i];
            Context.require(_value.signum() >= 0 && _balanceOf(_from, _id).compareTo(_value) >= 0,
                    "Insufficient funds");

            // Transfer funds
            DictDB<String, BigInteger> balance = bdbBalances.at(_id);
            balance.set(_from.toString(), _balanceOf(_from, _id).subtract(_value));
            balance.set(_to.toString(), _balanceOf(_to, _id).add(_value));
        }

        // Emit event
        x_TransferBatch(_caller.toString(), _from.toString(), _to.toString(), rlpEncode(_ids), rlpEncode(_values));

        // Try to call onIRC31BatchReceived
        // this will only work for local network contract addresses, so we wrap it in a
        // try/catch
        try {
            Address toAddress = Address.fromString(_to.getAddress());
            if (toAddress.isContract()) {
                // Call {@code onIRC31Received} if the recipient is a contract
                Context.call(Address.fromString(_to.getAddress()), "onIRC31BatchReceived", Address.fromString(_caller.getAddress()), Address.fromString(_from.getAddress()), _ids, _values,
                        _data == null ? new byte[] {} : _data);
            }
        } catch (Exception ex) {
        }
    }

    /**
     * This is the original IRC-31 implementation of setApprovalForAll, which looks up the newly implemented NetworkAddress based on the configured Network ID and the _from, _to and caller.
     * Required to match the IRC-31 interface requirements
     * @param _operator: an ICON Address of the intended operator
     * @param _approved: a boolean value indicating approval or not
     */
    @External
    public void setApprovalForAll(Address _operator, boolean _approved) {
        Context.require(!varNetworkID.getOrDefault("").isEmpty(), "The Network ID is not configured for this SCORE");
        NetworkAddress operatorAddress = new NetworkAddress(_operator, varNetworkID.get());
        NetworkAddress callerAddress = new NetworkAddress(Context.getCaller(), varNetworkID.get());
        _setApprovalForAll(callerAddress, operatorAddress, _approved);
    }

    /**
     A XCall compatible implementation of the setApprovalForAll function
     * This implementation can accept any address, network address or btp address in string format
     * @param _operator: an address in one of the following formats: an ICON address in string format, a network address ([NetworkID]/[Address]) or btp address ([btp://][NetworkID]/[Address])
     * @param _approved: a boolean value indicating approval or not
     */
    @External
    public void x_setApprovalForAll(String _operator, boolean _approved) {
        Context.require(!varNetworkID.getOrDefault("").isEmpty(), "The Network ID is not configured for this SCORE");
        NetworkAddress operatorAddress = new NetworkAddress(_operator, varNetworkID.get());
        NetworkAddress callerAddress = new NetworkAddress(Context.getCaller(), varNetworkID.get());
        _setApprovalForAll(callerAddress, operatorAddress, _approved);
    }

    private void _setApprovalForAll(NetworkAddress _caller, NetworkAddress _operator, boolean _approved) {
        bdbOperatorApproval.at(_caller.toString()).set(_operator.toString(), _approved);
        x_ApprovalForAll(_caller.toString(), _operator.toString(), _approved);
    }

    /**
     * This is the original IRC-31 implementation of isApprovedForAll, which looks up the newly implemented NetworkAddress based on the configured Network ID and the _from, _to and caller.
     * Required to match the IRC-31 interface requirements
     * @param _owner: an ICON Address of the owner
     * @param _operator: an ICON Address of the intended operator
     */
    @External(readonly = true)
    public boolean isApprovedForAll(Address _owner, Address _operator) {
        Context.require(!varNetworkID.getOrDefault("").isEmpty(), "The Network ID is not configured for this SCORE");
        NetworkAddress ownerAddress = new NetworkAddress(_owner, varNetworkID.get());
        NetworkAddress operatorAddress = new NetworkAddress(_operator, varNetworkID.get());
        return _isApprovedForAll(ownerAddress, operatorAddress);
    }

    /**
     A XCall compatible implementation of the isApprovedForAll function
     * This implementation can accept any address, network address or btp address in string format
     * @param _owner: an address in one of the following formats: an ICON address in string format, a network address ([NetworkID]/[Address]) or btp address ([btp://][NetworkID]/[Address])
     * @param _operator: an address in one of the following formats: an ICON address in string format, a network address ([NetworkID]/[Address]) or btp address ([btp://][NetworkID]/[Address])
     */
    @External(readonly = true)
    public boolean x_isApprovedForAll(String _owner, String _operator) {
        Context.require(!varNetworkID.getOrDefault("").isEmpty(), "The Network ID is not configured for this SCORE");
        NetworkAddress ownerAddress = new NetworkAddress(_owner, varNetworkID.get());
        NetworkAddress operatorAddress = new NetworkAddress(_operator, varNetworkID.get());
        return _isApprovedForAll(ownerAddress, operatorAddress);
    }

    private boolean _isApprovedForAll(NetworkAddress _owner, NetworkAddress _operator) {
        return bdbOperatorApproval.at(_owner.toString()).getOrDefault(_operator.toString(), false);
    }

    /**
     * Creates a new token type and assigns _amount to creator
     *
     * @param _id: ID of the token
     * @param _amount: The amount of tokens to mint
     * @param _uri: The token URI
     */
    @External
    public void mint(BigInteger _id, BigInteger _amount, String _uri) {
        Context.require(!varNetworkID.getOrDefault("").isEmpty(), "The Network ID is not configured for this SCORE");
        NetworkAddress _owner = new NetworkAddress(Context.getCaller(), varNetworkID.get());
        _mint(_owner, _id, _amount, _uri);
    }

    protected void _mint(NetworkAddress _owner, BigInteger _id, BigInteger _amount, String _uri) {
        Context.require(dbCreators.get(_id) == null, "Token is already minted");
        Context.require(_amount.compareTo(BigInteger.ZERO) > 0, "Amount should be positive");

        // Mint the token & update balances
        _mintInternal(_owner, _id, _amount); 
        // Set token URI
        _setdbTokenURI(_id, _uri);
    }

    private void _mintInternal(NetworkAddress owner, BigInteger id, BigInteger amount) {
        Context.require(amount.compareTo(BigInteger.ZERO) > 0, "Invalid amount");

        // Update creator
        dbCreators.set(id, owner);
     
        BigInteger balance = _balanceOf(owner, id);
        bdbBalances.at(id).set(owner.toString(), balance.add(amount));
        
        // Emit transfer event for mint semantic
        x_TransferSingle(owner.toString(), new NetworkAddress(ZERO_ADDRESS, varNetworkID.get()).toString(), owner.toString(), id, amount);
    }

    protected void _mintBatch(NetworkAddress owner, BigInteger[] ids, BigInteger[] amounts) {
        Context.require(ids.length == amounts.length, "id/amount pairs mismatch");

        for (int i = 0; i < ids.length; i++) {
            BigInteger id = ids[i];
            BigInteger amount = amounts[i];
            _mintInternal(owner, id, amount); //This will error out because we need to expect the URI value
        }

        // emit transfer event for Mint semantic
        x_TransferBatch(owner.toString(), new NetworkAddress(ZERO_ADDRESS, varNetworkID.get()).toString(), owner.toString(), rlpEncode(ids),
                rlpEncode(amounts));
    }

    /**
     * Destroys tokens for a given amount
     *
     * @param _id     ID of the token
     * @param _amount The amount of tokens to burn
     */
    @External
    public void burn(BigInteger _id, BigInteger _amount) {
        NetworkAddress _owner = new NetworkAddress(Context.getCaller(), varNetworkID.get());
        _burn(_owner, _id, _amount);
    }

    protected void _burn(NetworkAddress owner, BigInteger id, BigInteger amount) {
        _burnInternal(owner, id, amount);
        // emit transfer event for Burn semantic
        x_TransferSingle(owner.toString(), owner.toString(), new NetworkAddress(ZERO_ADDRESS, varNetworkID.get()).toString(), id, amount);
    }

    private void _burnInternal(NetworkAddress owner, BigInteger id, BigInteger amount) {
        Context.require(amount.compareTo(BigInteger.ZERO) > 0, "Invalid amount");

        BigInteger balance = _balanceOf(owner, id);
        Context.require(balance.compareTo(amount) >= 0, "Insufficient funds");
        bdbBalances.at(id).set(owner.toString(), balance.subtract(amount));
    }

    protected void _burnBatch(NetworkAddress owner, BigInteger[] ids, BigInteger[] amounts) {
        Context.require(ids.length == amounts.length, "id/amount pairs mismatch");

        for (int i = 0; i < ids.length; i++) {
            BigInteger id = ids[i];
            BigInteger amount = amounts[i];
            _burnInternal(owner, id, amount);
        }

        // emit transfer event for Burn semantic
        x_TransferBatch(owner.toString(), owner.toString(), new NetworkAddress(ZERO_ADDRESS, varNetworkID.get()).toString(), rlpEncode(ids),
                rlpEncode(amounts));
    }

    /**
     * Updates the given token URI
     *
     * @param _id  ID of the token
     * @param _uri The token URI
     */
    @External
    public void setTokenURI(BigInteger _id, String _uri) {
        NetworkAddress _caller = new NetworkAddress(Context.getCaller(), varNetworkID.get());
        _setTokenURI(_caller, _id, _uri);
    }

    private void _setTokenURI(NetworkAddress _caller, BigInteger _id, String _uri) {
        Context.require(_caller.equals(dbCreators.get(_id)), "Not token creator");
        _setdbTokenURI(_id, _uri);
    }

    /***
     * Sets a token URI
     */
    protected void _setdbTokenURI(BigInteger _id, String _uri) {
        Context.require(_uri.length() > 0, "_uri cannot be blank or null");
        dbTokenURI.set(_id, _uri);
        this.URI(_id, _uri);
    }

    // ================================================
    // XCall Implementations
    // ================================================

    /*
     * Handles the call message received from the source chain
     * can only be called from the XCall service address
     * 
     * @param _from The exterenal address of the caller on the source chain
     * 
     * @param _data The calldata delivered from the caller in the following JSON
     * format (required values are based on the intended method):
     * {
     * method: "methodName", //required
     * data : {
     * _from: "", // A btp/network address string
     * _to: "", // A btp/network address string
     * _operator: "", // A btp/network address string
     * _owner: "", // A btp/network address string
     * _ids: [], // An array of BigInteger values representing tokenIDs
     * _values: [], //An array of BigInteger values representing a value (in case of
     * transfers or minting), the array length should match the _id length
     * _data: "", // an encoded byte array string
     * _approved: // 0x0 or 0x1 indicating true or false
     * }
     * }
     */
    @External
    public void handleCallMessage(String _from, byte[] _data) {
        onlyCallService();

        NetworkAddress callerAddress = new NetworkAddress(_from, ""); // We don't need to pass in the network ID because
                                                                     // the _from variable is expected to be in btp
                                                                     // address format.

        String dataString = new String(_data);
        JsonObject requestObject = null;
        JsonObject requestData = null;

        try {
            requestObject = Json.parse(dataString).asObject();
            requestData = requestObject.get("data").asObject();
        } catch (Exception ex) {
            Context.revert("_data does not appear to be in the expected JSON format error:" + ex.getMessage());
        }

        if (requestObject == null)
            Context.revert("_data does not appear to be in an expected JSON format or is empty.");

        if (requestData == null)
            Context.revert("_data does not contain required data token in a json format or it is empty.");

        String method = requestObject.get("method").asString();
        Context.require(method.length() > 0, "method token cannot be empty in _data");

        
        if (method.equals("transferFrom") || method.equals("transferFromBatch")) {
            _handleTransferMessage(callerAddress, requestData, method);
        } 
        else  if (method.equals("setApprovalForAll")) {
            _handleSetApprovalForAllMessage(callerAddress, requestData);
        } else {
            Context.revert("Method '" + method + "' is not supported");
        }
    }

    private void _handleTransferMessage(NetworkAddress caller, JsonObject requestData, String method)
    {
        // _transferFrom(NetworkAddress _from, NetworkAddress _to, BigInteger _id,
        // BigInteger _value, @Optional byte[] _data)
        Context.require(requestData.contains("_to"), "_to token missing in data for method transferFrom");
        Context.require(requestData.contains("_from"), "_from token missing in data for method transferFrom");
        Context.require(requestData.contains("_ids"), "_ids token missing in data for method transferFrom");
        Context.require(requestData.contains("_values"), "_values token missing in data for method transferFrom");

        NetworkAddress toAddress = new NetworkAddress(requestData.get("_to").asString(), "");
        NetworkAddress fromAddress = new NetworkAddress(requestData.get("_from").asString(), "");
        JsonArray ids = requestData.get("_ids").asArray();
        JsonArray values = requestData.get("_values").asArray();

        Context.require(ids.size() == values.size(),
                "_ids & _values length mismatch, both arrays should be the same size");

        BigInteger[] idArray = new BigInteger[ids.size()];
        BigInteger[] valueArray = new BigInteger[values.size()];

        // Convert both values into useable biginteger arrays
        for (int i = 0; i < idArray.length; i++) {
            idArray[i] = new BigInteger(ids.get(i).asString().replace("0x", ""),16);
            valueArray[i] = new BigInteger(values.get(i).asString().replace("0x", ""),16);
        }

        byte[] dataBytes =  new byte[] {};

        if (requestData.contains("_data"))
            dataBytes = requestData.get("_data").asString().getBytes();

        if (method.equals("transferFrom")) {
            _transferFrom(caller, fromAddress, toAddress, idArray[0], valueArray[0], dataBytes);
        } else if (method.equals("transferFromBatch")) {
            _transferFromBatch(caller, fromAddress, toAddress, idArray, valueArray, dataBytes);
        }
    }

    private void _handleSetApprovalForAllMessage(NetworkAddress _caller, JsonObject _data)
    {
        //_setApprovalForAll(NetowrkAddress _caller, NetworkAddres _operator, boolean _approved)
        Context.require(_data.contains("_operator"), "_operator token missing in data for method transferFrom");
        Context.require(_data.contains("_approved"), "_approved token missing in data for method transferFrom");

        NetworkAddress operatorAddress = new NetworkAddress(_data.get("_operator").asString(), "");
        String approvedString = _data.get("_approved").asString();
        Boolean approved = approvedString.equals("0x1") ? true : false;

        _setApprovalForAll(_caller, operatorAddress, approved);     
    }

    // ================================================
    // Event Logs
    // ================================================

    @EventLog(indexed = 3)
    public void x_TransferSingle(String _operator, String _from, String _to, BigInteger _id,
            BigInteger _value) {
    }
    
    @EventLog(indexed = 3)
    public void TransferSingle(Address _operator, Address _from, Address _to, BigInteger _id,
            BigInteger _value) {
    }

    @EventLog(indexed = 3)
    public void x_TransferBatch(String _operator, String _from, String _to, byte[] _ids,
            byte[] _values) {
    }

    @EventLog(indexed = 3)
    public void TransferBatch(Address _operator, Address _from, Address _to, byte[] _ids,
            byte[] _values) {
    }

    @EventLog(indexed = 2)
    public void x_ApprovalForAll(String _owner, String _operator, boolean _approved) {
    }

    @EventLog(indexed = 2)
    public void ApprovalForAll(Address _owner, Address _operator, boolean _approved) {
    }

    @EventLog(indexed = 1)
    public void URI(BigInteger _id, String _value) {
    }

    // ================================================
    // Utility Methods
    // ================================================

    /**
     * When called, will only allow score owner to submit the transaction
     */
    private void onlyOwner() {
        Context.require(Context.getCaller().equals(Context.getOwner()), "Caller is not the SCORE owner.");
    }

    /**
     * When called, only allows the method to be called by the configured xcall
     * service contract
     */
    private void onlyCallService() {
        Context.require(!varXCallContract.getOrDefault(ZERO_ADDRESS).equals(ZERO_ADDRESS),
                "XCall contract is not configured.");
        Context.require(Context.getCaller().equals(varXCallContract.get()),
                "Caller is not the configured XCall contract  (" + varXCallContract.get().toString() + ")");
    }

    /**
     * Convert a list of BigInteger to a RLP-encoded byte array
     * 
     * @param ids A list of BigInteger
     * @return a RLP encoded byte array
     */
    protected static byte[] rlpEncode(BigInteger[] ids) {
        Context.require(ids != null);

        ByteArrayObjectWriter writer = Context.newByteArrayObjectWriter("RLPn");

        writer.beginList(ids.length);
        for (BigInteger v : ids) {
            writer.write(v);
        }
        writer.end();

        return writer.toByteArray();
    }

    /**
     * Loops through the ArrayDB object to find a specific value
     * 
     * @param <T>     The class type of the array being checked
     * @param value   the value to check for
     * @param arraydb the array object to check
     * @return boolean indicating item is found (true), or not (false)
     */
    public static <T> Boolean containsInArrayDb(T value, ArrayDB<T> arraydb) {
        boolean found = false;
        if (arraydb == null || value == null) {
            return found;
        }

        for (int i = 0; i < arraydb.size(); i++) {
            if (arraydb.get(i) != null
                    && arraydb.get(i).equals(value)) {
                found = true;
                break;
            }
        }
        return found;
    }
}