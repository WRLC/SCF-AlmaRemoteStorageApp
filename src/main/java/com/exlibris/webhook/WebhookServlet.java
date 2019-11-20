package com.exlibris.webhook;

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.net.util.Base64;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import com.exlibris.configuration.ConfigurationHandler;

/**
 * Servlet implementation class MainServlet
 */
@WebServlet("/webhook")
public class WebhookServlet extends HttpServlet {

	final Charset charSet = Charset.forName("UTF-8");

	private static final long serialVersionUID = 1L;
	final private static Logger logger = Logger.getLogger(WebhookServlet.class);

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String challenge = req.getParameter("challenge");
		logger.info("challenge is :" + challenge);
		resp.setContentType("application/json");
		JSONObject json = new JSONObject();
		json.put("challenge", challenge);
		resp.getWriter().write(json.toString());

	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse resp) throws ServletException, IOException {

		JSONObject props = ConfigurationHandler.getInstance().getConfiguration();
		String secret = props.get("webhook_secret").toString();
		String signature = request.getHeader("X-Exl-Signature");

		String str;
		String body = "";
		while ((str = request.getReader().readLine()) != null) {
			body += str;
		}
		logger.info("message is :" + body);
		resp.getWriter().write("message is :" + body);
		final String message = body;
		boolean validateSignature = false;
		try {
			validateSignature = validateSignature(message, secret, signature);
		} catch (Exception e) {
		}
		if (!validateSignature) {
			resp.getWriter().write("un validate signature");
			logger.info("un validate signature");
			return;
		}
		Runnable runner = new Runnable() {
			public void run() {
				ManageWebHookMessages.getWebhookMessage(message);
			}
		};

		Thread thread = new Thread(runner);
		thread.start();
		logger.info("webhook handler ended");

	}

	private boolean validateSignature(String message, String secret, String signature) throws Exception {
		Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
		SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
		sha256_HMAC.init(secret_key);
		String hash = Base64.encodeBase64URLSafeString(sha256_HMAC.doFinal(message.getBytes())) + "=";
		return hash.equals(signature);
	}
}