/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.api.impl.search

import com.waz.api
import com.waz.api.impl.{ConnectionsSearch, Contacts, OnlyContactsBySearchKeyFiltering}
import com.waz.model.{Handle, SearchQuery}
import com.waz.service.SearchKey
import com.waz.ui.UiModule

class Search(implicit ui: UiModule) extends api.Search {
  override def getTopPeople(limit: Int, filter: Array[String]): api.UserSearchResult =
    new UserSearchResult(SearchQuery.TopPeople, limit, filter.toSet)

  override def getRecommendedPeople(query: String, limit: Int, filter: Array[String]): api.UserSearchResult = {
    val searchQuery = query match {
      case Handle.handlePattern(term) => SearchQuery.RecommendedHandle(term)
      case _ => SearchQuery.Recommended(query)
    }
    new UserSearchResult(searchQuery, limit, filter.toSet)
  }

  override def getGroupConversations(query: String, limit: Int): api.ConversationSearchResult = {
    new ConversationSearchResult(query, limit, Handle.containsSymbol(query))
  }

  override def getContacts(query: String): api.Contacts =
    new Contacts(OnlyContactsBySearchKeyFiltering(SearchKey(query)))

  override def getConnectionsByName(query: String, limit: Int, filter: Array[String]): api.UserSearchResult =
    new ConnectionsSearch(query, limit, filter, false, false, Handle.containsSymbol(query))


  override def getConnectionsByNameOrEmailIncludingBlocked(query: String, limit: Int, filter: Array[String]): api.UserSearchResult =
    new ConnectionsSearch(query, limit, filter, true, true, Handle.containsSymbol(query))
}
