package top.truism.blog.rpc.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * RPC 响应体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RpcResponse implements Serializable {

    public static final byte STATUS_SUCCESS = 0;
    public static final byte STATUS_FAIL = 1;

    /** 响应状态 */
    private byte status;

    /** 正常返回值 */
    private Object data;

    /** 异常信息（status != 0 时有值） */
    private String errorMessage;

    public static RpcResponse success(Object data) {
        return RpcResponse.builder()
                .status(STATUS_SUCCESS)
                .data(data)
                .build();
    }

    public static RpcResponse fail(String errorMessage) {
        return RpcResponse.builder()
                .status(STATUS_FAIL)
                .errorMessage(errorMessage)
                .build();
    }
}
