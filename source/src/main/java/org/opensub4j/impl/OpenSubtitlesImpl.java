/**
 *    Copyright (c) 2015 Wojciech Tekiela
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.opensub4j.impl;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.opensub4j.api.OpenSubtitles;
import org.opensub4j.file.FileHashCalculator;
import org.opensub4j.parser.ResponseObjectBuilderFactory;
import org.opensub4j.parser.ResponseParser;
import org.opensub4j.parser.ResponseParserImpl;
import org.opensub4j.response.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OpenSubtitlesImpl implements OpenSubtitles {

    private static final String ANONYMOUS = "";

    private final XmlRpcClient client;
    private final ResponseParser parser;
    private final ResponseObjectBuilderFactory builderFactory;

    private LoginToken loginToken;

    public OpenSubtitlesImpl(URL serverUrl) {
        this.client = new RetriableXmlRpcClient(serverUrl);
        this.parser = new ResponseParserImpl();
        this.builderFactory = new ResponseObjectBuilderFactory();
    }

    @Override
    public ServerInfo serverInfo() throws XmlRpcException {
        Object[] params = {};
        Object response = client.execute("ServerInfo", params);

        // note: no response status in server info

        if (!(response instanceof Map)) {
            //throw new Exception("Unable to get server info, malformed response");
            return null;
        }

        return parser.parse(builderFactory.serverInfoBuilder(), response);
    }

    @Override
    public void login(String lang, String useragent) throws XmlRpcException {
        login(ANONYMOUS, ANONYMOUS, lang, useragent);
    }

    @Override
    public void login(String user, String pass, String lang, String useragent) throws XmlRpcException {
        if (loginToken != null) {
            throw new IllegalStateException("Already logged in! Please log out first.");
        }

        Object[] params = {user, pass, lang, useragent};
        Object response = client.execute("LogIn", params);

        loginToken = parser.parse(builderFactory.loginTokenBuilder(), response);
    }

    @Override
    public void logout() throws XmlRpcException {
        ensureLoggedIn();

        Object[] params = {loginToken.getToken()};
        Object response = client.execute("LogOut", params);

        loginToken = null;
    }

    @Override
    public void noop() throws XmlRpcException {
        ensureLoggedIn();

        Object[] params = {loginToken.getToken()};
        Object response = client.execute("NoOperation", params);
    }

    @Override
    public List<SubtitleInfo> searchSubtitles(String lang, File file, String imdbId, String query) throws IOException, XmlRpcException {
        String hash = null;
        long size = 0;

        if (file != null) {
            hash = FileHashCalculator.calculateHash(file);
            size = file.length();
        }

        return searchSubtitles(lang, size, hash, imdbId, query);
    }

    @Override
    public List<SubtitleInfo> searchSubtitles(String lang, long fileSize, String fileHash, String imdbId, String query) throws IOException, XmlRpcException {
        ensureLoggedIn();

        Map<String, String> videoProperties = new HashMap<>();
        videoProperties.put("sublanguageid", lang);
        if (fileHash != null) {
            videoProperties.put("moviehash", fileHash);
        }
        if (fileSize > 0) {
            videoProperties.put("moviebytesize", String.valueOf(fileSize));
        }
        if (imdbId != null) {
            videoProperties.put("imdbid", imdbId);
        }
        if (query != null) {
            videoProperties.put("query", query);
        }

        Object[] videoParams = {videoProperties};
        Object[] params = {loginToken.getToken(), videoParams};
        Object response = client.execute("SearchSubtitles", params);

        return parser.parse(builderFactory.subtitleInfoListBuilder(parser), response);
    }

    @Override
    public List<SubtitleFile> downloadSubtitles(int subtitleFileID) throws XmlRpcException {
        ensureLoggedIn();

        Object[] subtitleFileIDs = {subtitleFileID};
        Object[] params = {loginToken.getToken(), subtitleFileIDs};
        Object response = client.execute("DownloadSubtitles", params);

        return parser.parse(builderFactory.subtitleFileListBuilder(parser), response);
    }

    @Override
    public List<MovieInfo> searchMoviesOnImdb(String query) throws XmlRpcException {
        ensureLoggedIn();

        Object[] params = {loginToken.getToken(), query};
        Object response = client.execute("SearchMoviesOnIMDB", params);

        return parser.parse(builderFactory.movieInfoListBuilder(parser), response);
    }

    private void ensureLoggedIn() {
        if (loginToken == null) {
            throw new IllegalStateException("Not logged in!");
        }
    }

}
