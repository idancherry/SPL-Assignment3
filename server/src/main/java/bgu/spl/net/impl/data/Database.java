package bgu.spl.net.impl.data;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public class Database {
	private final String sqlHost;
	private final int sqlPort;
	private final java.util.concurrent.atomic.AtomicInteger nextMessageId =
        new java.util.concurrent.atomic.AtomicInteger(1);
	
	private final ConcurrentHashMap<Integer, String> activeUsers = new ConcurrentHashMap<>();


	// Map of channel to set of subscribed connectionIds
	private final ConcurrentHashMap<String, Set<Integer>> channelSubscribers;

	// Map of connectionId to (subscriptionId to channel)
	private final ConcurrentHashMap<Integer, Map<String, String>> clientSubscriptions;

	private Database() {
		channelSubscribers = new ConcurrentHashMap<>();
		clientSubscriptions = new ConcurrentHashMap<>();
		// SQL server connection details
		this.sqlHost = "127.0.0.1";
		this.sqlPort = 7778;
	}

	public static Database getInstance() {
		return Instance.instance;
	}

	/**
	 * Execute SQL query and return result
	 * @param sql SQL query string
	 * @return Result string from SQL server
	 */
	private String executeSQL(String sql) {
		try (Socket socket = new Socket(sqlHost, sqlPort);
			 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
			 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
			
			// Send SQL with null terminator
			out.print(sql + '\0');
			out.flush();
			
			// Read response until null terminator
			StringBuilder response = new StringBuilder();
			int ch;
			while ((ch = in.read()) != -1 && ch != '\0') {
				response.append((char) ch);
			}
			
			return response.toString();
			
		} catch (Exception e) {
			System.err.println("SQL Error: " + e.getMessage());
			return "ERROR:" + e.getMessage();
		}
	}

	public void addChannel(String channel) {
		channelSubscribers.putIfAbsent(channel, ConcurrentHashMap.newKeySet());
	}

	public void subscribe(int connectionId, String channel, String subscriptionId) {
		channelSubscribers.putIfAbsent(channel, ConcurrentHashMap.newKeySet());
		channelSubscribers.get(channel).add(connectionId);
		
		clientSubscriptions.putIfAbsent(connectionId, new ConcurrentHashMap<>());
		clientSubscriptions.get(connectionId).put(subscriptionId, channel);
	}

	public void unsubscribe(int connectionId, String subscriptionId) {
		Map<String, String> subscriptions = clientSubscriptions.get(connectionId);
		if (subscriptions == null) {
			return;
		}
		String channel = subscriptions.get(subscriptionId);
		if (channel != null) {
			if (channelSubscribers.containsKey(channel)) {
				channelSubscribers.get(channel).remove(connectionId);
			}
			if (clientSubscriptions.containsKey(connectionId)) {
				clientSubscriptions.get(connectionId).remove(subscriptionId);
			}
			if (channelSubscribers.get(channel).isEmpty()) {
				channelSubscribers.remove(channel);
			}
		}
	}

	public Set<Integer> getChannelSubscribers(String channel) {
		Set<Integer> subscribers = channelSubscribers.getOrDefault(channel, ConcurrentHashMap.newKeySet());
		return subscribers.stream().collect(java.util.stream.Collectors.toSet());
	}

	public void disconnect(int connectionsId){
		Map<String, String> subscriptions = clientSubscriptions.get(connectionsId);
		if (subscriptions != null) {
			Set<String> subscriptionIds = subscriptions.keySet().stream().collect(java.util.stream.Collectors.toSet());
			for (String subscriptionId : subscriptionIds) {
				unsubscribe(connectionsId, subscriptionId);
			}
		}
		clientSubscriptions.remove(connectionsId);
	}

	public boolean isUserConnected(int connectionId) {
		return activeUsers.containsKey(connectionId);
	}

	public String getUsername(int connectionId) {
		return activeUsers.get(connectionId);
	}

	public int getNextMessageId() {
		return nextMessageId.getAndIncrement();
	}

	public String getSubscription(int connectionId, String dest){
		Map<String, String> subscriptions = clientSubscriptions.get(connectionId);
		if (subscriptions != null) {
			for (Map.Entry<String, String> entry : subscriptions.entrySet()) {
				if (entry.getValue().equals(dest)) {
					return entry.getKey();
				}
			}
		}
		return null;
	}

	public boolean isSubscribed(int connectionId, String dest) {
		Map<String, String> subscriptions = clientSubscriptions.get(connectionId);
		if (subscriptions != null) {
			return subscriptions.containsValue(dest);
		}
		return false;
	}

	/**
	 * Escape SQL special characters to prevent SQL injection
	 */
	private String escapeSql(String str) {
		if (str == null) return "";
		return str.replace("'", "''");
	}

	public LoginStatus login(int connectionId, String username, String password) {
		if (activeUsers.containsKey(connectionId)) {
        	return LoginStatus.CLIENT_ALREADY_CONNECTED;
    	}
		String checkSql = String.format(
        "SELECT password FROM users WHERE username='%s'",
        escapeSql(username)
    	);
		String result = executeSQL(checkSql);

		if (result.startsWith("ERROR")) {
			return LoginStatus.WRONG_PASSWORD;
		}

		boolean userExists = result.startsWith("SUCCESS|");
		
		if (!userExists) {
			String insertUser = String.format(
				"INSERT INTO users (username, password, registration_date) VALUES ('%s','%s',datetime('now','localtime'))",
				escapeSql(username), escapeSql(password)
			);
			executeSQL(insertUser);
		} else {
			String storedPassword = result.split("\\|")[1]
										.replace("(", "")
										.replace(")", "")
										.replace("'", "")
										.split(",")[0];

			if (!storedPassword.equals(password)) {
				return LoginStatus.WRONG_PASSWORD;
			}
		}

		if (activeUsers.containsValue(username)) {
			return LoginStatus.ALREADY_LOGGED_IN;
		}

		String logLogin = String.format(
			"INSERT INTO login_history (username, login_time) VALUES ('%s', datetime('now'))",
			escapeSql(username)
		);
		executeSQL(logLogin);
		activeUsers.put(connectionId, username);

		return userExists
			? LoginStatus.LOGGED_IN_SUCCESSFULLY
			: LoginStatus.ADDED_NEW_USER;
	}

	

	public void logout(int connectionsId) {
		String username = activeUsers.remove(connectionsId);
		if (username == null) return;

		String sql = String.format(
			"UPDATE login_history SET logout_time=datetime('now') " +
			"WHERE username='%s' AND logout_time IS NULL " +
			"ORDER BY login_time DESC LIMIT 1",
			escapeSql(username)
		);
		executeSQL(sql);
	}

	/**
	 * Track file upload in SQL database
	 * @param username User who uploaded the file
	 * @param filename Name of the file
	 * @param gameChannel Game channel the file was reported to
	 */
	public void trackFileUpload(String username, String filename, String gameChannel) {
		String sql = String.format(
			"INSERT INTO file_tracking (username, filename, upload_time, game_channel) " +
			"VALUES ('%s', '%s', datetime('now'), '%s')",
			escapeSql(username), escapeSql(filename), escapeSql(gameChannel)
		);
		executeSQL(sql);
	}

	/**
	 * Generate and print server report using SQL queries
	 */
	public void printReport() {
		System.out.println(repeat("=", 80));
		System.out.println("SERVER REPORT - Generated at: " + java.time.LocalDateTime.now());
		System.out.println(repeat("=", 80));
		
		// List all users
		System.out.println("\n1. REGISTERED USERS:");
		System.out.println(repeat("-", 80));
		String usersSQL = "SELECT username, registration_date FROM users ORDER BY registration_date";
		String usersResult = executeSQL(usersSQL);
		if (usersResult.startsWith("SUCCESS")) {
			String[] parts = usersResult.split("\\|");
			if (parts.length > 1) {
				for (int i = 1; i < parts.length; i++) {
					System.out.println("   " + parts[i]);
				}
			} else {
				System.out.println("   No users registered");
			}
		}
		
		// Login history for each user
		System.out.println("\n2. LOGIN HISTORY:");
		System.out.println(repeat("-", 80));
		String loginSQL = "SELECT username, login_time, logout_time FROM login_history ORDER BY username, login_time DESC";
		String loginResult = executeSQL(loginSQL);
		if (loginResult.startsWith("SUCCESS")) {
			String[] parts = loginResult.split("\\|");
			if (parts.length > 1) {
				String currentUser = "";
				for (int i = 1; i < parts.length; i++) {
					String[] fields = parts[i].replace("(", "").replace(")", "").replace("'", "").split(", ");
					if (fields.length >= 3) {
						if (!fields[0].equals(currentUser)) {
							currentUser = fields[0];
							System.out.println("\n   User: " + currentUser);
						}
						System.out.println("      Login:  " + fields[1]);
						System.out.println("      Logout: " + (fields[2].equals("None") ? "Still logged in" : fields[2]));
					}
				}
			} else {
				System.out.println("   No login history");
			}
		}
		
		// File uploads for each user
		System.out.println("\n3. FILE UPLOADS:");
		System.out.println(repeat("-", 80));
		String filesSQL = "SELECT username, filename, upload_time, game_channel FROM file_tracking ORDER BY username, upload_time DESC";
		String filesResult = executeSQL(filesSQL);
		if (filesResult.startsWith("SUCCESS")) {
			String[] parts = filesResult.split("\\|");
			if (parts.length > 1) {
				String currentUser = "";
				for (int i = 1; i < parts.length; i++) {
					String[] fields = parts[i].replace("(", "").replace(")", "").replace("'", "").split(", ");
					if (fields.length >= 4) {
						if (!fields[0].equals(currentUser)) {
							currentUser = fields[0];
							System.out.println("\n   User: " + currentUser);
						}
						System.out.println("      File: " + fields[1]);
						System.out.println("      Time: " + fields[2]);
						System.out.println("      Game: " + fields[3]);
						System.out.println();
					}
				}
			} else {
				System.out.println("   No files uploaded");
			}
		}
		
	System.out.println(repeat("=", 80));
}

private String repeat(String str, int times) {
	StringBuilder sb = new StringBuilder();
	for (int i = 0; i < times; i++) {
		sb.append(str);
	}
	return sb.toString();
}

private static class Instance {
	static Database instance = new Database();
}}
