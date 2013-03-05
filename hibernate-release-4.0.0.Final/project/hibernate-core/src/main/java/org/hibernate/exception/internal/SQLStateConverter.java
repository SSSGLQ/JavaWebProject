/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2008-2011, Red Hat Inc. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.exception.internal;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.JDBCException;
import org.hibernate.PessimisticLockException;
import org.hibernate.QueryTimeoutException;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.DataException;
import org.hibernate.exception.GenericJDBCException;
import org.hibernate.exception.JDBCConnectionException;
import org.hibernate.exception.LockAcquisitionException;
import org.hibernate.exception.SQLGrammarException;
import org.hibernate.exception.spi.SQLExceptionConverter;
import org.hibernate.exception.spi.ViolatedConstraintNameExtracter;
import org.hibernate.internal.util.JdbcExceptionHelper;

/**
 * A SQLExceptionConverter implementation which performs converion based on
 * the underlying SQLState. Interpretation of a SQL error based on SQLState
 * is not nearly as accurate as using the ErrorCode (which is, however, vendor-
 * specific).  Use of a ErrorCode-based converter should be preferred approach
 * for converting/interpreting SQLExceptions.
 *
 * @author Steve Ebersole
 */
public class SQLStateConverter implements SQLExceptionConverter {

	private ViolatedConstraintNameExtracter extracter;

	private static final Set SQL_GRAMMAR_CATEGORIES = new HashSet();
	private static final Set DATA_CATEGORIES = new HashSet();
	private static final Set INTEGRITY_VIOLATION_CATEGORIES = new HashSet();
	private static final Set CONNECTION_CATEGORIES = new HashSet();

	static {
		SQL_GRAMMAR_CATEGORIES.add( "07" );
		SQL_GRAMMAR_CATEGORIES.add( "37" );
		SQL_GRAMMAR_CATEGORIES.add( "42" );
		SQL_GRAMMAR_CATEGORIES.add( "65" );
		SQL_GRAMMAR_CATEGORIES.add( "S0" );
		SQL_GRAMMAR_CATEGORIES.add( "20" );
		
		DATA_CATEGORIES.add("22");
		DATA_CATEGORIES.add("21");
		DATA_CATEGORIES.add("02");

		INTEGRITY_VIOLATION_CATEGORIES.add( "23" );
		INTEGRITY_VIOLATION_CATEGORIES.add( "27" );
		INTEGRITY_VIOLATION_CATEGORIES.add( "44" );

		CONNECTION_CATEGORIES.add( "08" );
	}

	public SQLStateConverter(ViolatedConstraintNameExtracter extracter) {
		this.extracter = extracter;
	}

	/**
	 * Convert the given SQLException into Hibernate's JDBCException hierarchy.
	 *
	 * @param sqlException The SQLException to be converted.
	 * @param message      An optional error message.
	 * @param sql          Optionally, the sql being performed when the exception occurred.
	 * @return The resulting JDBCException.
	 */
	public JDBCException convert(SQLException sqlException, String message, String sql) {
		String sqlState = JdbcExceptionHelper.extractSqlState( sqlException );

		if ( sqlState != null ) {
			String sqlStateClassCode = JdbcExceptionHelper.determineSqlStateClassCode( sqlState );

			if ( sqlStateClassCode != null ) {
				if ( SQL_GRAMMAR_CATEGORIES.contains( sqlStateClassCode ) ) {
					return new SQLGrammarException( message, sqlException, sql );
				}
				else if ( INTEGRITY_VIOLATION_CATEGORIES.contains( sqlStateClassCode ) ) {
					String constraintName = extracter.extractConstraintName( sqlException );
					return new ConstraintViolationException( message, sqlException, sql, constraintName );
				}
				else if ( CONNECTION_CATEGORIES.contains( sqlStateClassCode ) ) {
					return new JDBCConnectionException( message, sqlException, sql );
				}
				else if ( DATA_CATEGORIES.contains( sqlStateClassCode ) ) {
					return new DataException( message, sqlException, sql );
				}
			}

			if ( "40001".equals( sqlState ) ) {
				return new LockAcquisitionException( message, sqlException, sql );
			}

			if ( "61000".equals( sqlState ) ) {
				// oracle sql-state code for deadlock
				return new LockAcquisitionException( message, sqlException, sql );
			}

			if ( "40XL1".equals( sqlState ) || "40XL2".equals( sqlState )) {
				// Derby "A lock could not be obtained within the time requested."
				return new PessimisticLockException( message, sqlException, sql );
			}

			// MySQL Query execution was interrupted
			if ( "70100".equals( sqlState ) ||
				// Oracle user requested cancel of current operation
				  "72000".equals( sqlState ) ) {
				throw new QueryTimeoutException(  message, sqlException, sql );
			}
		}

		return handledNonSpecificException( sqlException, message, sql );
	}

	/**
	 * Handle an exception not converted to a specific type based on the SQLState.
	 *
	 * @param sqlException The exception to be handled.
	 * @param message      An optional message
	 * @param sql          Optionally, the sql being performed when the exception occurred.
	 * @return The converted exception; should <b>never</b> be null.
	 */
	protected JDBCException handledNonSpecificException(SQLException sqlException, String message, String sql) {
		return new GenericJDBCException( message, sqlException, sql );
	}
}
