package org.ethereum.android.jsonrpc.full.method;

import com.thetransactioncompany.jsonrpc2.*;
import com.thetransactioncompany.jsonrpc2.server.*;
import org.ethereum.android.jsonrpc.full.JsonRpcServerMethod;
import org.ethereum.facade.Ethereum;

/*
TODO: not present clear specification for this method, also cpp and go version not provide it.
*/
public class shh_newGroup extends JsonRpcServerMethod {

    public shh_newGroup(Ethereum ethereum) {
        super(ethereum);
    }

    protected JSONRPC2Response worker(JSONRPC2Request req, MessageContext ctx) {

        JSONRPC2Response res = new JSONRPC2Response(JSONRPC2Error.METHOD_NOT_FOUND, req.getID());
        return res;

    }
}