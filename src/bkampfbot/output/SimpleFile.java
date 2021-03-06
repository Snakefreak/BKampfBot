package bkampfbot.output;

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

public class SimpleFile extends AbstractFile {

	protected SimpleFile(String filename) {
		super(filename);
	}

	public static void write(String filename, String content) {
		SimpleFile s = new SimpleFile(filename);
		s.write(content);
	}

	public static void append(String filename, String content) {
		SimpleFile s = new SimpleFile(filename);
		s.append(content);
	}
}
