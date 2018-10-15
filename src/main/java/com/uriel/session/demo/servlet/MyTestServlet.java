/**
 * 
 */
package com.uriel.session.demo.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

/**
 * @author Uriel Santoyo
 *
 */
@WebServlet("/app/greeting.do")
public class MyTestServlet extends HttpServlet {

	/**
	 * Serial
	 */
	private static final long serialVersionUID = -2534048178348646994L;
	private static final Logger log = Logger.getLogger(MyTestServlet.class);

	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		log.info("Request -->");
		PrintWriter out = resp.getWriter();
		out.println(new Date() + "</br>");
		out.println("Hello!");
		out.flush();
	}

	
}
