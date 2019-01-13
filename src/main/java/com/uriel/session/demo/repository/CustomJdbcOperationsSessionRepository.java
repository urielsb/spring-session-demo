/**
 * 
 */
package com.uriel.session.demo.repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.support.lob.DefaultLobHandler;
import org.springframework.jdbc.support.lob.LobHandler;
import org.springframework.session.ExpiringSession;
import org.springframework.session.MapSession;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.session.jdbc.JdbcOperationsSessionRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Add suport to publish {@link SessionExpiredEvent} events to
 * {@link JdbcOperationsSessionRepository}.
 * 
 * @author Uriel Santoyo
 *
 */
public class CustomJdbcOperationsSessionRepository extends JdbcOperationsSessionRepository {

	private static Logger LOG = Logger.getLogger(CustomJdbcOperationsSessionRepository.class);

	private static final String SELECT_EXPIRED_SESSIONS_QUERY = "SELECT S.SESSION_ID, S.CREATION_TIME, S.LAST_ACCESS_TIME, "
			+ "	S.MAX_INACTIVE_INTERVAL, SA.ATTRIBUTE_NAME, SA.ATTRIBUTE_BYTES "
			+ "FROM SPRING_SESSION  S "
			+ "LEFT OUTER JOIN SPRING_SESSION_ATTRIBUTES SA ON S.SESSION_ID = SA.SESSION_ID "
			+ "WHERE S.MAX_INACTIVE_INTERVAL < (? - S.LAST_ACCESS_TIME) / 1000";

	@Autowired
	private ApplicationEventPublisher eventPublisher;
	final private JdbcOperations jdbcOperations;
	final private TransactionOperations transactionOperations;
	private LobHandler lobHandler = new DefaultLobHandler();
	private final ResultSetExtractor<List<ExpiringSession>> extractor =
			new ExpiringSessionResultSetExtractor();

	public CustomJdbcOperationsSessionRepository(DataSource dataSource, PlatformTransactionManager transactionManager) {
		super(dataSource, transactionManager);
		this.jdbcOperations = createDefaultJdbcTemplate(dataSource);
		this.transactionOperations = createTransactionTemplate(transactionManager);
	}

	@Override
	public void cleanUpExpiredSessions() {
		LOG.info("Overriden method cleanUpExpiredSessions()");

		final long time = System.currentTimeMillis();
		List<ExpiringSession> expiredSessions = transactionOperations
				.execute(new TransactionCallback<List<ExpiringSession>>() {

					@Override
					public List<ExpiringSession> doInTransaction(TransactionStatus status) {
						List<ExpiringSession> sessions = jdbcOperations.query(SELECT_EXPIRED_SESSIONS_QUERY,
								new PreparedStatementSetter() {

									@Override
									public void setValues(PreparedStatement ps) throws SQLException {
										LOG.debug("Current time parameter -> " + time);
										ps.setLong(1, time);
									}
								}, extractor);
						LOG.debug("Expired Sessions -> " + sessions);
						return sessions;
					}
				});
		
		if(expiredSessions != null && !expiredSessions.isEmpty()) {
			for (ExpiringSession expiringSession : expiredSessions) {
				SessionExpiredEvent event = new SessionExpiredEvent(CustomJdbcOperationsSessionRepository.this, expiringSession);
				eventPublisher.publishEvent(event);
			}
			
			super.cleanUpExpiredSessions();
		}
	}

	private static JdbcTemplate createDefaultJdbcTemplate(DataSource dataSource) {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		jdbcTemplate.afterPropertiesSet();
		return jdbcTemplate;
	}

	private static TransactionTemplate createTransactionTemplate(PlatformTransactionManager transactionManager) {
		TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
		transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		transactionTemplate.afterPropertiesSet();
		return transactionTemplate;
	}

	private class ExpiringSessionResultSetExtractor implements ResultSetExtractor<List<ExpiringSession>> {

		public List<ExpiringSession> extractData(ResultSet rs) throws SQLException, DataAccessException {
			List<ExpiringSession> sessions = new ArrayList<ExpiringSession>();
			while (rs.next()) {
				String id = rs.getString("SESSION_ID");
				MapSession session;
				if (sessions.size() > 0 && getLast(sessions).getId().equals(id)) {
					session = (MapSession) getLast(sessions);
				} else {
					session = new MapSession(id);
					session.setCreationTime(rs.getLong("CREATION_TIME"));
					session.setLastAccessedTime(rs.getLong("LAST_ACCESS_TIME"));
					session.setMaxInactiveIntervalInSeconds(rs.getInt("MAX_INACTIVE_INTERVAL"));
				}
				String attributeName = rs.getString("ATTRIBUTE_NAME");
				if (attributeName != null) {
					session.setAttribute(attributeName, deserialize(rs, "ATTRIBUTE_BYTES"));
				}
				sessions.add(session);
			}
			return sessions;
		}

		private ExpiringSession getLast(List<ExpiringSession> sessions) {
			return sessions.get(sessions.size() - 1);
		}

	}
	
	private Object deserialize(ResultSet rs, String columnName)
			throws SQLException {
		
		ConversionService conversionService = createDefaultConversionService();
		return conversionService.convert(
				this.lobHandler.getBlobAsBytes(rs, columnName),
				TypeDescriptor.valueOf(byte[].class),
				TypeDescriptor.valueOf(Object.class));
	}
	
	private static GenericConversionService createDefaultConversionService() {
		GenericConversionService converter = new GenericConversionService();
		converter.addConverter(Object.class, byte[].class,
				new SerializingConverter());
		converter.addConverter(byte[].class, Object.class,
				new DeserializingConverter());
		return converter;
	}

}
