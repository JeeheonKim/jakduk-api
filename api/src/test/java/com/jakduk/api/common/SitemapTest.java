package com.jakduk.api.common;

import com.redfin.sitemapgenerator.ChangeFreq;
import com.redfin.sitemapgenerator.WebSitemapGenerator;
import com.redfin.sitemapgenerator.WebSitemapUrl;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.util.ObjectUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.MalformedURLException;
import java.util.Date;

/**
 * Created by pyohwanjang on 2017. 3. 12..
 */

@RunWith(MockitoJUnitRunner.class)
public class SitemapTest {

    @Test
    public void generateSitemap() {
        try {
            WebSitemapGenerator wsg = new WebSitemapGenerator("https://jakduk.com");
            WebSitemapUrl url = new WebSitemapUrl.Options("https://jakduk.com/board/free/1")
                    .lastMod(new Date()).priority(1.0).changeFreq(ChangeFreq.DAILY).build();

            wsg.addUrl(url);

            Assert.assertTrue(! ObjectUtils.isEmpty(wsg.writeAsStrings()));

        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void URL생성() {
        UriComponents uriComponents = UriComponentsBuilder.fromHttpUrl("https://localhost:8080")
                .path("/{path1}/{path2}")
                .buildAndExpand("path1", "path2");

        Assert.assertTrue(uriComponents.toUriString().equals("https://localhost:8080/path1/path2"));
    }

}
