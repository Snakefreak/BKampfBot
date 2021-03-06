package bkampfbot.plan;

/*
 Copyright (C) 2011  georf@georf.de

 This file is part of BKampfBot.

 BKampfBot is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 2 of the License, or
 any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import json.JSONArray;
import json.JSONException;
import json.JSONObject;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import bkampfbot.Utils;
import bkampfbot.exceptions.BadOpponent;
import bkampfbot.exceptions.ConfigError;
import bkampfbot.exceptions.FatalError;
import bkampfbot.exceptions.RestartLater;
import bkampfbot.output.Output;
import bkampfbot.state.Config;
import bkampfbot.state.Opponent;
import bkampfbot.state.User;
import bkampfbot.utils.AngriffOptions;
import bkampfbot.utils.Keilerei;
import bkampfbot.utils.OpponentCache;

/**
 * PlanAngriff benötigt folgende Konfiguration:
 * {"Angriff":{"Verein":1,"Stufe":1,"Respekt":1000000,"Freunde":true}}
 * 
 * Standard ist: {"Angriff":{"Verein":0,"Stufe":0,"Respekt":-1,"Freunde":false}}
 * 
 * @author georf
 * 
 */
public final class PlanAngriff extends PlanObject {
	public enum Club {
		No, Yes, Whatever
	};

	/**
	 * Vereinszugehörigkeit No Yes Whatever
	 */
	private Club club = Club.Whatever;

	/**
	 * Levelunterschied
	 */
	private int level = 0;

	/**
	 * Maximaler Respekt
	 */
	private int hpMax = -1;
	private int hpMin = -1;

	/**
	 * Soll auf Freunde überprüft werden?
	 */
	private boolean friend = false;

	/**
	 * Weitere Optionen
	 */
	private final AngriffOptions options;

	/**
	 * Welches Bundesland?
	 */
	private String raceToFight = null;

	/**
	 * Für späteren Zugriff auf Ergebnis
	 */
	private boolean won = false;

	private OpponentCache oppCache;

	/**
	 * 
	 * @param object
	 * @throws FatalError
	 */
	public PlanAngriff(JSONObject angriff, String scope) throws FatalError {
		super("Angriff");

		// {"Angriff":{"Verein":1,"Stufe":1,"Respekt":1000000,
		// "Land":"Mecklenburg-Vorpommern"}}

		try {
			int c = angriff.getInt("Verein");
			if (c == 0)
				this.club = Club.Whatever;
			else if (c == 1)
				this.club = Club.Yes;
			else if (c == -1)
				this.club = Club.No;
			else
				new ConfigError("Verein muss 1,-1 oder 0 sein");
		} catch (JSONException r) {
		}

		try {
			this.level = angriff.getInt("Stufe");
		} catch (JSONException r) {
		}

		// Attribute havn't set
		try {
			hpMax = angriff.getInt("Respekt");
		} catch (JSONException r) {
			try {
				JSONObject help = angriff.getJSONObject("Respekt");

				try {
					hpMax = help.getInt("Max");
				} catch (JSONException h) {
				}

				try {
					hpMax = help.getInt("max");
				} catch (JSONException h) {
				}

				try {
					hpMin = help.getInt("Min");
				} catch (JSONException h) {
				}

				try {
					hpMin = help.getInt("min");
				} catch (JSONException h) {
				}
			} catch (JSONException z) {
			}
		}

		try {
			this.friend = angriff.getBoolean("Freunde");
		} catch (JSONException r) {
		}

		try {
			this.raceToFight = angriff.getString("Land");
		} catch (JSONException r) {
		}

		options = new AngriffOptions(angriff);

		// Cache holen
		oppCache = OpponentCache.getInstance(scope);
	}

	public final void run() throws FatalError, RestartLater {
		Output.printClockLn("-> Angriff (Verein: "
				+ (this.club.equals(Club.Yes) ? "Ja" : "")
				+ (this.club.equals(Club.No) ? "Nein" : "")
				+ (this.club.equals(Club.Whatever) ? "Egal" : "") + ", Level: "
				+ String.valueOf(this.level + User.getLevel())
				+ (this.friend ? ", Freunde" : "")
				+ (raceToFight != null ? ", Land: " + raceToFight : "")
				+ (this.hpMax > 0 ? ", Respekt-Max: " + this.hpMax : "")
				+ (this.hpMin > 0 ? ", Respekt-Min: " + this.hpMin : "") + ")",
				Output.INFO);

		if (!Utils.fightAvailable(15)) {
			return;
		}

		try {
			boolean result = false;
			for (int i = 0; i < 30 && !result; i++) {
				if (i != 0)
					Output.printTabLn(
							"Didn't find someone to fight. Will try again.", 1);
				result = completeFight();
			}
			if (!result)
				Output.printTabLn(
						"Will abort fighting. (Didn't find an opponent.)", 1);
		} catch (NoList l) {
			Output.println("Will abort fighting. (No list avalible.)", 0);
		}
	}

	/**
	 * Try to find an opponent and attack him.
	 * 
	 * @return false if nobody was attack
	 * @throws NoList
	 *             if no list is avalible
	 * @throws RestartLater
	 * @throws FatalError
	 * @throws IOException
	 */
	private final boolean completeFight() throws NoList, FatalError,
			RestartLater {
		Utils.visit("fights/start");

		// search a opponent on the "high money" list
		Opponent opp = oppCache.findHighMoney();
		if (opp != null) {
			try {
				int money = Keilerei.fight(opp, "Angriff nochmal", options,
						this);

				// Fight was won, we safe the opponent
				if (money > Config.getFightAgain()) {
					this.won = true;

					// Fight was lost
				} else if (money == -1) {
					this.won = false;

					// remove from list
					oppCache.removeHighMoney(opp);

					// add to bad list
					oppCache.addBad(opp);

					// Fight was won, but with low money
				} else {
					this.won = true;

					oppCache.removeHighMoney(opp);
					oppCache.addLowMoney(opp);
				}
				return true;
			} catch (BadOpponent e) {
				// set done for today
				opp.setDone();
				return false;
			}
		}

		// Falls wir nach Freunden suchen sollen, machen wir das vorher
		String friends[] = new String[0];
		if (this.friend) {
			friends = this.getFriendList();
		}

		try {
			int level = this.level + User.getLevel();

			if (level < 1)
				level = 1;

			JSONArray arr;
			if (raceToFight != null) {
				arr = getList(level, Utils.raceToId(raceToFight));
			} else {
				arr = getList(level);
			}
			JSONObject now;

			for (int a = 0; a < 3; a++) {
				// If list is avalible
				if (arr.length() > 0) {
					for (int i = 0; i < arr.length(); i++) {
						now = arr.getJSONObject(i);

						opp = new Opponent(now.getString("name"), now
								.getString("attack"));

						boolean guild;
						try {
							now.getInt("guild");
							guild = false;
						} catch (JSONException e) {
							guild = true;
						}

						if (now.getInt("level") == level
								&& !now.getString("race").equalsIgnoreCase(
										User.getRace())
								&& !now.getString("race").equalsIgnoreCase(
										User.getRaceSecondary())
								&& (this.club.equals(Club.Whatever)
										|| (guild && this.club.equals(Club.Yes)) || (!guild && this.club
										.equals(Club.No)))
								&& !oppCache.isBadOpponent(opp)
								&& (this.hpMax <= 0 || Integer.valueOf(now
										.getString("hp")) < this.hpMax)
								&& (this.hpMin <= 0 || Integer.valueOf(now
										.getString("hp")) > this.hpMin)
								&& !this.isFriend(friends, now
										.getString("name"))) {
							try {

								int result = Keilerei.fight(opp, "Angriff",
										options, this);
								// Fight was won, we safe the opponent
								if (result > Config.getFightAgain()) {
									oppCache.addHighMoney(opp);

									this.won = true;

									// Fight was lost
								} else if (result == -1) {
									// save, here we don't need the name
									oppCache.addLowMoney(opp);

									this.won = false;
								} else {
									// save, here we don't need the name
									oppCache.addLowMoney(opp);

									this.won = true;
								}
							} catch (BadOpponent e) {
								oppCache.addBad(opp);
							}

							return true;
						}
					}
				} else {
					throw new NoList();
				}

				// we get a new list in 1 second
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
				}

				if (raceToFight != null) {
					arr = getList(level, Utils.raceToId(raceToFight));
				} else {
					arr = this.getList(level);
				}
			}
		} catch (JSONException e) {
		}
		return false;
	}

	/**
	 * Überprüft ob ein Gegner auf der Liste ist
	 * 
	 * @param list
	 * @param name
	 * @return boolean
	 */
	private final boolean isFriend(String[] list, String name) {
		for (int i = 0; i < list.length; i++) {
			if (list[i].equalsIgnoreCase(name)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * List die Freundesliste aus und gibt sie als String-Array zurück.
	 * 
	 * @return String[]
	 */
	private final String[] getFriendList() {
		try {
			// {"list":[{"profil":"","msg":"","id":"","signup_id":"","level":"","race":"","name":"","hp":"","online":""}],"nexturl":null,"prevurl":null}
			Output.printTabLn("Hole Freundesliste", Output.DEBUG);

			JSONArray ret = new JSONArray();

			String nexturl = "characters/friendsListJson";

			while (true) {
				JSONObject ob = Utils.getJSON(nexturl);

				JSONArray list = ob.getJSONArray("list");

				for (int i = 0; i < list.length(); i++) {
					ret.put(list.get(i));
				}

				nexturl = ob.getString("nexturl");

				if (nexturl == null || nexturl.equals("null")) {
					break;
				}
			}

			String[] list = new String[ret.length()];
			for (int i = 0; i < ret.length(); i++) {
				JSONObject now = ret.getJSONObject(i);
				list[i] = now.getString("name");
			}
			return list;
		} catch (JSONException e) {
			return new String[0];
		}

	}

	/**
	 * Try to get the list of opponents for the given level.
	 * 
	 * Reads club from the config.
	 * 
	 * @param current
	 *            instance
	 * @param level
	 * @return the list of opponents as JSON
	 * @throws NoList
	 */
	private final JSONArray getList(int level) throws NoList {
		return getList(level, 0);
	}

	private final JSONArray getList(int level, int race) throws NoList {
		Output.printTabLn("Hole Gegnerliste", 2);

		try {
			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
			nvps
					.add(new BasicNameValuePair("selectRace", String
							.valueOf(race)));
			nvps.add(new BasicNameValuePair("selectLevel", String
					.valueOf(level)));
			nvps.add(new BasicNameValuePair("selectGuildFilter", String
					.valueOf(getGuildFilter())));
			nvps.add(new BasicNameValuePair("selectHp", String
					.valueOf(getHpFilter())));

			JSONArray arr;

			JSONObject ob = Utils.getJSON("fights/opponentsListJson", nvps);
			arr = ob.getJSONArray("list");

			return arr;

		} catch (JSONException e) {
			Output.printTabLn("No list avalible", 1);
			Output.error(e);
		}
		throw new NoList();

		/**
		 * {"list":[ { "attack":"\/fights\/start\/53599",
		 * "profil":"\/characters\/profile\/EDGLM", "id":"53599",
		 * "signup_id":"53599", "race":"Nordrhein-Westfalen", "level":"2",
		 * "enemy":0, "friend":0, "guild":0, "name":"LaLa26", "hp":"82"},[...]],
		 */
	}

	/**
	 * We have to initiate this class.
	 */
	public final static void initiate() {
		OpponentCache.reset();
	}

	private final class NoList extends Exception {
		private static final long serialVersionUID = -2317742009841698364L;
	}

	private final short getGuildFilter() {
		switch (club) {
		default:
		case Whatever:
			return 0;
		case No:
			return 1;
		case Yes:
			return 2;
		}
	}

	private final int getHpFilter() {
		int[] hps = { 0, 1000, 5000, 10000, 20000, 50000, 100000, 150000,
				200000, 250000, 300000, 350000, 400000, 450000, 500000, 600000,
				700000, 800000, 900000, 1000000, 1200000, 1400000, 1600000,
				1800000, 2000000, 2250000, 2500000, 2750000, 3000000, 3500000,
				4000000, 4500000, 5000000, 6000000, 7000000, 8000000, 9000000,
				10000000 };

		if (hpMax < 1)
			return 0;
		if (hpMax < 1000)
			return 1;
		if (hpMin < 1)
			return 0;

		for (int j = 1; j < hps.length; j++) {
			if (hps[j - 1] > hpMin && hps[j] < hpMax) {
				return j;
			}
		}

		return 0;
	}

	public boolean won() {
		return this.won;
	}
}
