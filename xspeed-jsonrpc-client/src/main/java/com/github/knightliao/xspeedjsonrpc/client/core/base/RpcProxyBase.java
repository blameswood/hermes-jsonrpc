package com.github.knightliao.xspeedjsonrpc.client.core.base;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.knightliao.xspeedjsonrpc.core.constant.Constants;
import com.github.knightliao.xspeedjsonrpc.core.exception.ExceptionHandler;
import com.github.knightliao.xspeedjsonrpc.core.exception.InternalErrorException;
import com.github.knightliao.xspeedjsonrpc.core.exception.JsonRpcException;
import com.github.knightliao.xspeedjsonrpc.core.exception.ParseErrorException;
import com.github.knightliao.xspeedjsonrpc.core.exception.ServerErrorException;
import com.github.knightliao.xspeedjsonrpc.core.gson.GsonFactory;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * JsonRpc调用端的公共基类，包含绝大部分rpc调用的实现
 * 
 * 
 */
public abstract class RpcProxyBase implements InvocationHandler, Cloneable {

    final static Gson gson = GsonFactory.getGson();

    protected ExceptionHandler exceptionHandler = new ExceptionHandler();

    protected AtomicInteger counter = new AtomicInteger();
    protected String encoding;
    protected String url;
    protected int _connectTimeout = -1; // 默认连接超时30秒
    protected int _readTimeout = -1; // 默认连接超时60秒

    /**
     * 设置连接超时(ms)
     * 
     */
    public void setConnectTimeout(int v) {
        _connectTimeout = v;
    }

    /**
     * 获取连接超时设置(ms)
     * 
     */
    public int getConnectTimeout() {
        return _connectTimeout;
    }

    /**
     * 获取读超时设置(ms)
     * 
     */
    public void setReadTimeout(int v) {
        _readTimeout = v;
    }

    /**
     * 设置读超时(ms)/c
     * 
     */
    public int getReadTimeout() {
        return _readTimeout;
    }

    /**
     * 将二进制协议数据反序列化成JsonElement树 由子类覆盖
     * 
     * @param req
     * @return 生成的JsonElement树
     * @throws ParseErrorException
     */
    protected abstract JsonElement deserialize(byte[] req)
            throws ParseErrorException;

    /**
     * 将JsonElement树反序列化成二进制协议数据 由子类覆盖
     * 
     * @param res
     * @return 生成的二进制协议数据
     * @throws ParseErrorException
     */
    protected abstract byte[] serialize(JsonElement res)
            throws ParseErrorException;

    /**
     * 返回所处理的contentType() 由子类覆盖
     * 
     * @return 所处理的MIME类类型，
     */
    protected abstract String contentType();

    /**
     * @param url
     *            服务的url
     * @param encoding
     *            编码
     * @param exceptionHandler
     *            异常处理器
     */
    public RpcProxyBase(String url, String encoding,
            ExceptionHandler exceptionHandler) {
        this.encoding = encoding;
        this.url = url;
        this.exceptionHandler = exceptionHandler;
        this.counter.set(new Random().nextInt());

    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.reflect.InvocationHandler#invoke(java.lang.Object,
     * java.lang.reflect.Method, java.lang.Object[])
     */
    public Object invoke(Object proxy, Method method, Object[] args)
            throws Throwable {

        try {

            int id = counter.getAndIncrement();

            //
            // 组装原请求
            //
            JsonElement request = makeRequest(id, method, args);

            //
            // 序列化
            //
            byte[] reqBytes = serialize(request);
            // log.debug("request bytes size is " + reqBytes.length);

            //
            // 连接
            //
            HttpURLConnection connection = (HttpURLConnection) new URL(url)
                    .openConnection();
            if (_connectTimeout > 0) {
                connection.setConnectTimeout(_connectTimeout);
            }
            if (_readTimeout > 0) {
                connection.setReadTimeout(_readTimeout);
            }

            //
            // 发送请求
            //
            sendRequest(reqBytes, connection);

            //
            // 反序列化
            //
            byte[] resBytes = null;
            resBytes = readResponse(connection);
            JsonElement resJson = deserialize(resBytes);

            //
            // 解析结果
            //
            return parseResult(id, resJson, method);

        } catch (IOException e) {
            throw new InternalErrorException(e);
        }
    }

    /**
     * 读取服务器返回的信息
     * 
     * @param connection
     * @return 读取到的数据
     * @throws IOException
     * @throws JsonRpcException
     */
    protected byte[] readResponse(URLConnection connection)
            throws JsonRpcException {

        byte[] resBytes;
        InputStream in = null;

        HttpURLConnection httpconnection = (HttpURLConnection) connection;

        try {

            if (httpconnection.getResponseCode() == HttpURLConnection.HTTP_OK) {

                in = httpconnection.getInputStream();

            } else {

                if (httpconnection.getContentType().equals(contentType())
                        && httpconnection.getErrorStream() != null) {
                    in = httpconnection.getErrorStream();
                } else {
                    in = httpconnection.getInputStream();
                }
            }

            int len = httpconnection.getContentLength();
            if (len <= 0) {
                throw new InternalErrorException("no response to get.");
            }

            resBytes = new byte[len];
            int offset = 0;
            while (offset < resBytes.length) {
                int bytesRead = in.read(resBytes, offset, resBytes.length
                        - offset);
                if (bytesRead == -1) {
                    break; // end of stream
                }
                offset += bytesRead;
            }

            if (offset <= 0) {
                throw new InternalErrorException("there is no service to "
                        + url);
            }

            // log.debug("response bytes size is " + offset);

        } catch (IOException e) {

            throw new InternalErrorException(e);

        } finally {

            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    throw new InternalErrorException(e);
                }
            }
        }
        return resBytes;
    }

    /**
     * 向服务器发送信息
     * 
     * @param reqBytes
     * @param connection
     * @throws IOException
     */
    protected void sendRequest(byte[] reqBytes, URLConnection connection) {

        HttpURLConnection httpconnection = (HttpURLConnection) connection;
        OutputStream out = null;

        try {
            httpconnection.setRequestMethod("POST");
            httpconnection.setUseCaches(false);
            httpconnection.setDoInput(true);
            httpconnection.setDoOutput(true);
            httpconnection.setRequestProperty("Content-Type", contentType()
                    + ";charset=" + encoding);
            httpconnection.setRequestProperty("Content-Length", ""
                    + reqBytes.length);
            httpconnection.connect();
            out = httpconnection.getOutputStream();
            out.write(reqBytes);

        } catch (Exception e) {

            throw new InternalErrorException(e);

        } finally {

            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    throw new InternalErrorException(e);
                }
            }
        }
    }

    /**
     * 组装rpc数据报
     * 
     * @param method
     * @param args
     * @return 生成的请求数据
     * @throws ParseErrorException
     */
    protected JsonElement makeRequest(int id, Method method, Object[] args)
            throws ParseErrorException {

        String name = method.getName();

        Map<String, Object> map = new HashMap<String, Object>();

        // protocol
        map.put(Constants.JSONRPC_PROTOCOL, Constants.JSONRPC_PROTOCOL_VERSION);

        // method name
        map.put(Constants.JSONRPC_METHOD, name);

        // args
        if (args != null) {
            map.put(Constants.JSONRPC_PARAM, args);
        } else {
            map.put(Constants.JSONRPC_PARAM, new Object[0]);
        }

        // id
        map.put(Constants.JSONRPC_ID, "" + id);

        return gson.toJsonTree(map);
    }

    /**
     * 处理接受到的rpc数据报
     * 
     * @param ele
     * @param method
     * @return 调用的返回值
     * @throws Exception
     */
    protected Object parseResult(int id, JsonElement ele, Method method)
            throws Exception {

        JsonObject res = (JsonObject) ele;

        //
        // 版本
        //
        if (!res.get(Constants.JSONRPC_PROTOCOL).getAsString()
                .equals(Constants.JSONRPC_PROTOCOL_VERSION)) {
            throw new InternalErrorException();
        }

        //
        // 返回
        //
        JsonElement result = res.get(Constants.JSON_RESULT);
        if (result != null) {

            if (res.get(Constants.JSONRPC_ID).getAsInt() != id) {
                throw new InternalErrorException("no id in response");
            } else {

                //
                // 反射
                //
                return gson.fromJson(result, method.getGenericReturnType());
            }

        } else {

            //
            // 出错
            //
            JsonElement e = res.get(Constants.JSON_RESULT_ERROR);

            if (e != null) {

                //
                // 是否是服务器异常
                //
                JsonRpcException jre = exceptionHandler.deserialize(e);
                if (jre instanceof ServerErrorException) {
                    String msg = jre.getMessage();
                    Class<?>[] exp_types = method.getExceptionTypes();
                    for (Class<?> exp_type : exp_types) {
                        if (msg.equals(exp_type.getSimpleName())) {
                            Exception custom_exp = (Exception) exp_type
                                    .newInstance();
                            custom_exp.initCause(jre.getCause());
                            throw custom_exp;
                        }
                    }
                }

                //
                // 非正常
                //
                throw jre;

            } else {

                //
                // 非正常
                //
                throw new InternalErrorException("no error or result returned");
            }
        }
    }

    /**
     * 
     */
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
