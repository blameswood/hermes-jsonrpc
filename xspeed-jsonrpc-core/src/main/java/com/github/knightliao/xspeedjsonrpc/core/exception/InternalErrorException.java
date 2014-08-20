package com.github.knightliao.xspeedjsonrpc.core.exception;

/**
 * 当Rpc出现其他内部错误时抛出
 * 
 */
public class InternalErrorException extends JsonRpcException {

    public static final int INTERNAL_ERROR_CODE = -32603;

    public InternalErrorException() {
        super();
    }

    public InternalErrorException(String message, Throwable cause) {
        super(message, cause);
    }

    public InternalErrorException(String message) {
        super(message);
    }

    public InternalErrorException(Throwable cause) {
        super(cause);
    }

    /**
	 * 
	 */
    private static final long serialVersionUID = -3629678837586531475L;

    /*
     * (non-Javadoc)
     * 
     * @see com.baidu.rpc.exception.JsonRpcException#errorCode()
     */
    @Override
    public int errorCode() {
        return INTERNAL_ERROR_CODE;
    }

}
