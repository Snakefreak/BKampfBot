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

import json.JSONException;
import json.JSONObject;
import bkampfbot.Utils;
import bkampfbot.exceptions.ConfigError;
import bkampfbot.exceptions.FatalError;
import bkampfbot.output.LogFile;
import bkampfbot.output.Output;

/**
 * PlanBooster benötigt folgende Konfiguration: {"Booster":3}
 * 
 * @author georf
 * 
 */

public final class PlanBooster extends PlanObject {

	private int booster;

	public PlanBooster(JSONObject object, Object boost) throws FatalError {
		super("Booster");

		if (boost != null && boost instanceof Integer) {
			booster = (Integer) boost;

		} else {
			throw new ConfigError("Booster");
		}

		switch (this.booster) {
		case 1:
		case 2:
		case 3:
		case 4:
		case 5:
		case 6:
			break;

		default:
			Output
					.println(
							"Sie haben einen falschen Boosterlevel gesetzt. Jetzt steht er auf 1.",
							0);
			this.booster = 1;
			break;
		}
	}

	public final void run() {
		try {
			printJump("" + booster);

			String s = Utils.getString("guild_challenge/index");
			int i = s.indexOf("var flashvars = {");
			i = s.indexOf("var flashvars = {", i + 1);
			i = s.indexOf("var flashvars = {", i + 1);
			s = s.substring(s.indexOf("guild_id:", i + 1));
			s = s.substring(0, s.indexOf('\n'));
			final String guild_id = s.replaceAll("[^0-9]", "");

			// visit guild_challenge/index
			JSONObject result = Utils.getJSON("guild_challenge/getData/"
					+ guild_id);

			if (result.getInt("booster") == 1) {
				JSONObject win = Utils.getJSON("guild_challenge/booster/"
						+ this.booster);

				if (win.getInt("win") == 1) {
					Output.printTabLn("Booster: WIN", 1);
				} else {
					Output.printTabLn("Booster: LOST", 1);
				}

				if (Output.isHtmlOutput()) {
					JSONObject log = LogFile.getLog("Booster", "x"
							+ this.booster);
					log.put("good", true);
					Output.addLog(log);
				}
			} else {
				Output.printTabLn("Booster is done.", 1);
			}
		} catch (JSONException e) {
			Output.error(e);
		}
	}

	@Override
	public boolean isPreRunningable() {
		return true;
	}
}
