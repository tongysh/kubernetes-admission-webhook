package com.tongysh.k8smutatingwebhook.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionRequest;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionResponse;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReview;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ValidatingWebhookController {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping(value = "/validate", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public AdmissionReview handleAdmissionRequest(@RequestBody AdmissionReview admissionReview) {
        // 提取请求
        AdmissionRequest request = admissionReview.getRequest();
        if (request == null) {
            return createErrorResponse("AdmissionRequest is null");
        }

        try {
            // 解析 Pod 对象
            Pod pod = objectMapper.readValue(objectMapper.writeValueAsString(request.getObject()), Pod.class);

            // 验证 Pod 是否包含特权容器
            boolean hasPrivileged = validatePod(pod);

            // 创建响应
            AdmissionResponse response = new AdmissionResponse();
            response.setUid(request.getUid());
            response.setAllowed(!hasPrivileged);  // 存在特权容器则拒绝

            if (hasPrivileged) {
                response.setStatus(new io.fabric8.kubernetes.api.model.StatusBuilder()
                        .withCode(403)
                        .withMessage("Privileged containers are forbidden")
                        .build());
            }

            // 返回处理结果
            AdmissionReview responseReview = new AdmissionReview();
            responseReview.setResponse(response);
            return responseReview;
        } catch (Exception e) {
            return createErrorResponse("Failed to process request: " + e.getMessage());
        }
    }

    private boolean validatePod(Pod pod) {
        // 检查所有容器是否有特权模式
        if (pod.getSpec() != null && pod.getSpec().getContainers() != null) {
            return pod.getSpec().getContainers().stream()
                    .anyMatch(container ->
                            container.getSecurityContext() != null &&
                                    Boolean.TRUE.equals(container.getSecurityContext().getPrivileged())
                    );
        }
        return false;  // 默认允许（无容器或无安全上下文）
    }

    private AdmissionReview createErrorResponse(String message) {
        AdmissionReview review = new AdmissionReview();
        AdmissionResponse response = new AdmissionResponse();
        response.setAllowed(false);
        response.setStatus(new io.fabric8.kubernetes.api.model.StatusBuilder()
                .withMessage(message)
                .build());
        review.setResponse(response);
        return review;
    }
}