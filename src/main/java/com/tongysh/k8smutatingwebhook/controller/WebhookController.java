package com.tongysh.k8smutatingwebhook.controller;


import cn.hutool.json.JSONUtil;
import cn.hutool.log.StaticLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionRequest;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionResponse;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReview;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/webhook")
public class WebhookController {


    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping(value = "/mutating", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public AdmissionReview handleAdmissionRequest(@RequestBody AdmissionReview admissionReview) {

        StaticLog.info("api-server传入参数：{}", JSONUtil.toJsonPrettyStr(admissionReview));

        // 提取请求
        AdmissionRequest request = admissionReview.getRequest();
        if (request == null) {
            return createErrorResponse("AdmissionRequest is null");
        }

        try {
            // 解析 Pod 对象
            Pod pod = objectMapper.readValue(objectMapper.writeValueAsString(request.getObject()), Pod.class);

            // 添加 env=dev 标签
            mutatePod(pod);

            StaticLog.info("mutating-webhook-pod处理后结果：{}", JSONUtil.toJsonPrettyStr(pod));

            // 创建响应
            AdmissionResponse response = new AdmissionResponse();
            response.setUid(request.getUid());
            response.setAllowed(true);
            response.setPatchType("JSONPatch");
            String base64Patch = Base64.getEncoder().encodeToString(generatePatch(pod).getBytes());
            response.setPatch(base64Patch);
//            response.setPatch(generatePatch(pod));

            // 返回处理结果
            AdmissionReview responseReview = new AdmissionReview();
            responseReview.setResponse(response);


            StaticLog.info("mutating-webhook响应结果：{}", JSONUtil.toJsonPrettyStr(responseReview));

            return responseReview;
        } catch (Exception e) {
            return createErrorResponse("Failed to process request: " + e.getMessage());
        }
    }

    private void mutatePod(Pod pod) {
        // 获取或初始化标签
        Map<String, String> labels = pod.getMetadata().getLabels();
        if (labels == null) {
            labels = new HashMap<>();
            pod.getMetadata().setLabels(labels);
        }

        // 添加或更新 env=dev 标签
        labels.put("env", "dev");
    }

    private String generatePatch(Pod pod) throws Exception {
        // 生成 JSON Patch 格式的修改
        Map<String, String> labels = pod.getMetadata().getLabels();
        return "[{\"op\":\"add\",\"path\":\"/metadata/labels\",\"value\":" +
                objectMapper.writeValueAsString(labels) + "}]";
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
