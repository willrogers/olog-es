/*
 * Copyright (C) 2020 European Spallation Source ERIC.
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package gov.bnl.olog.entity.preprocess;

import gov.bnl.olog.entity.Log;

/**
 * A pre-processor interface intended to ensure that all fields in the log entry have sensible values before
 * the entry is persisted.
 */
public interface LogPreprocessor {

    /**
     * Processes the log entry and returns a processed value.
     * @param log
     * @return
     */
    Log process(Log log);
}
