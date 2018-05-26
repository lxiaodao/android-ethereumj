package org.ethereum.android.jsonrpc.full.method;

import com.thetransactioncompany.jsonrpc2.*;
import com.thetransactioncompany.jsonrpc2.server.*;
import org.ethereum.android.jsonrpc.full.JsonRpcServerMethod;
import org.ethereum.android.jsonrpc.full.whisper.FilterManager;
import org.ethereum.facade.Ethereum;

import java.util.List;

public class shh_getMessages extends JsonRpcServerMethod {

    public shh_getMessages(Ethereum ethereum) {
        super(ethereum);
    }

    protected JSONRPC2Response worker(JSONRPC2Request req, MessageContext ctx) {

        List<Object> params = req.getPositionalParams();
        if (params.size() != 1) {
            return new JSONRPC2Response(JSONRPC2Error.INVALID_PARAMS, req.getID());
        } else {
            int id = jsToInt((String)params.get(0));
            JSONRPC2Response res = new JSONRPC2Response(FilterManager.getInstance().toJSAll(id), req.getID());
            return res;
        }

    }
}