/*
 *
 *  *  Copyright (c) 2019-2020, 冷冷 (wangiegie@gmail.com).
 *  *  <p>
 *  *  Licensed under the GNU Lesser General Public License 3.0 (the "License");
 *  *  you may not use this file except in compliance with the License.
 *  *  You may obtain a copy of the License at
 *  *  <p>
 *  * https://www.gnu.org/licenses/lgpl.html
 *  *  <p>
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package top.dearbo.web.log.aspect;


import cn.hutool.core.util.URLUtil;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import top.dearbo.common.core.data.JsonUtil;
import top.dearbo.web.core.SpringContextHolder;
import top.dearbo.web.core.WebServletUtils;
import top.dearbo.web.log.annotation.SysLog;
import top.dearbo.web.log.config.DefaultSysLogConfig;
import top.dearbo.web.log.config.SysLogConfig;
import top.dearbo.web.log.entity.SysLogEntity;
import top.dearbo.web.log.event.SysLogEvent;

import java.util.Date;

/**
 * 操作日志使用spring event异步入库
 * 可通过 web.config.aopLog.enabled 配置是否开启Aop 日志收集
 *
 * @author Bo
 */
@Aspect
public class SysLogAspect {

	private static final Logger logger = LoggerFactory.getLogger(SysLogAspect.class);

	private final SysLogConfig sysLogConfig;

	public SysLogAspect(@Autowired(required = false) SysLogConfig sysLogConfig) {
		if (sysLogConfig == null) {
			this.sysLogConfig = new DefaultSysLogConfig();
		} else {
			this.sysLogConfig = sysLogConfig;
		}
	}

	@Around("@annotation(sysLog)")
	public Object around(ProceedingJoinPoint point, SysLog sysLog) throws Throwable {
		String strClassName = point.getTarget().getClass().getName();
		String strMethodName = point.getSignature().getName();
		logger.debug("[类名]:{},[方法]:{}", strClassName, strMethodName);
		HttpServletRequest request = getHttpRequest();
		if (request != null && sysLogConfig != null && sysLogConfig.checkLog(request)) {
			SysLogEntity logVo = getSysLog(request, sysLog, sysLogConfig, point);
			// 发送异步日志事件
			long startTime = System.currentTimeMillis();
			logVo.setRequestDate(new Date(startTime));
			Object obj = null;
			Throwable currentThrowable = null;
			try {
				obj = point.proceed();
			} catch (Throwable throwable) {
				currentThrowable = throwable;
				String localizedMessage = throwable.getLocalizedMessage();
				logVo.setException(localizedMessage == null ? ExceptionUtils.getMessage(throwable) : localizedMessage);
			}
			logVo.setTime(System.currentTimeMillis() - startTime);
			logVo.setResultData(sysLogConfig.getResultData(obj));
			SpringContextHolder.publishEvent(new SysLogEvent(logVo));
			if (currentThrowable != null) {
				throw currentThrowable;
			}
			return obj;
		}
		return point.proceed();
	}

	public SysLogEntity getSysLog(HttpServletRequest request, SysLog sysLog, SysLogConfig sysLogConfig, ProceedingJoinPoint point) {
		SysLogEntity sysLogEntity = new SysLogEntity();
		sysLogEntity.setOperator(sysLogConfig.getUserName(request));
		sysLogEntity.setOperatorId(sysLogConfig.getUserId(request));
		sysLogEntity.setType(StringUtils.isBlank(sysLog.type()) ? sysLogConfig.getType(request) : sysLog.type());
		sysLogEntity.setApplicationName(StringUtils.isBlank(sysLog.applicationName()) ? getApplicationName() : sysLog.applicationName());
		sysLogEntity.setSubject(StringUtils.isBlank(sysLog.subject()) ? sysLogConfig.getSubject(request) : sysLog.subject());
		sysLogEntity.setRemark(sysLog.value());
		sysLogEntity.setIp(WebServletUtils.getIpAddress(request));
		sysLogEntity.setRequestUri(URLUtil.getPath(request.getRequestURI()));
		sysLogEntity.setMethod(request.getMethod());
		sysLogEntity.setUserAgent(request.getHeader("user-agent"));
		String contentType = request.getContentType();
		sysLogEntity.setContentType(contentType);
		sysLogEntity.setArgBody(getArgBody(point, false));
		if (sysLog.saveParam()) {
			//处理json数据
			if (contentType != null && contentType.contains(MediaType.APPLICATION_JSON_VALUE)) {
				sysLogEntity.setParams(getArgBody(point, true));
			} else {
				sysLogEntity.setParams(WebServletUtils.toRequestParams(request, sysLogConfig.excludeParamKey()));
			}
		}
		return sysLogEntity;
	}

	private String getApplicationName() {
		return SpringContextHolder.getApplicationContext().getEnvironment().getProperty("spring.application.name");
	}

	private String getArgBody(ProceedingJoinPoint point, boolean serializeNull) {
		Object[] pointArgs = point.getArgs();
		if (pointArgs != null && pointArgs.length > 0) {
			StringBuilder dataSb = new StringBuilder();
			int i = 0;
			for (Object argObj : pointArgs) {
				if (argObj != null) {
					if (argObj instanceof MultipartFile || argObj instanceof ServletRequest || argObj instanceof ServletResponse) {
						continue;
					}
					if (i > 0) {
						dataSb.append("&");
					} else {
						i++;
					}
					if (serializeNull) {
						dataSb.append(JsonUtil.toJsonSerializeNull(argObj));
					} else {
						dataSb.append(JsonUtil.toJson(argObj));
					}
				}
			}
			return dataSb.toString();
		}
		return null;
	}

	private HttpServletRequest getHttpRequest() {
		try {
			RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
			if (requestAttributes != null && requestAttributes instanceof ServletRequestAttributes) {
				return ((ServletRequestAttributes) requestAttributes).getRequest();
			}
		} catch (Exception e) {
			logger.error("RequestContextHolder 获取 HttpServletRequest 异常", e);
		}
		return null;
	}

    /*private void checkSysLogConfig() {
        if (sysLogConfig == null) {
            try {
                sysLogConfig = SpringContextHolder.getBean(SysLogConfig.class);
            } catch (Throwable e) {
                sysLogConfig = new DefaultSysLogConfig();
            }
        }
    }*/

}
