package server;

import java.util.StringTokenizer;

/**
 * Holds information about a client via its certificate
 * @author claudiu
 *
 */
public class ClientDetails {
	public String name, department, company, city, state, country, DN;

	public ClientDetails(String DN) {
		int index = 0;
		StringTokenizer st = new StringTokenizer(DN, " \t\r\n,=");
		while(st.hasMoreTokens()){
			String token = st.nextToken();
			index++;
			switch (index) {
			case 2:
				name = token;
				break;
			case 4:
				department = token;
				break;
			case 6:
				company = token;
				break;
			case 8:
				city = token;
				break;
			case 10:
				state = token;
				break;
			case 12:
				country = token;
				break;
			}
		}
	}

	public String toString() {
		String s = "";
		s += "Client " + name + ", " + department + ", " + company + ", " +
				city + ", " + state + ", " + country;
		return s;
	}
}
