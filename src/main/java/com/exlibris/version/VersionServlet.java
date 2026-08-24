package com.exlibris.version;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;

@WebServlet("/version")
public class VersionServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        AppVersionInfo info = AppVersionInfo.getInstance();
        JSONObject json = new JSONObject();
        json.put("app", info.getAppName());
        json.put("version", info.getVersion());
        json.put("buildTime", info.getBuildTime());
        json.put("gitBranch", info.getGitBranch());
        json.put("gitCommit", info.getGitCommit());

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(json.toString());
    }
}
