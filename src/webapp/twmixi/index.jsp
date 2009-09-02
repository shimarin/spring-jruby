<%@page contentType="text/html;charset=EUC-JP" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@taglib prefix="ruby" uri="http://www.stbbs.net/spring-jruby/JspTag"%>
<ruby:eval>
  count = params[:count]
  if count != nil && count.to_i != 0 then
    request[:count] = count
  else
    request[:count] = 20
  end

  twitter_id = params[:twitter_id]
  mixi_id = params[:mixi_id]

  if twitter_id == nil then
    twitter_id = cookies[:twitter_id]
  end
  if mixi_id == nil then
    mixi_id = cookies[:mixi_id]
  end
  
  if twitter_id != "" && twitter_id != nil && mixi_id != "" && mixi_id != nil then
    request[:twitter_id] = twitter_id
    request[:mixi_id] = mixi_id
  end

  if servletRequest.method == "POST" then
    # Cookieに IDを保存
	cookie = javax.servlet.http.Cookie.new("twitter_id", twitter_id)
	cookie.max_age = 60 * 60 * 24 * 30 # 一月
	servletResponse.addCookie(cookie)

	cookie = javax.servlet.http.Cookie.new("mixi_id", mixi_id)
	cookie.max_age = 60 * 60 * 24 * 30 # 一月
	servletResponse.addCookie(cookie)
    
    mintime = nil
    maxtime = nil 
    year = nil
    month = nil
    day = nil
    days = []
    # Twitter APIではなく RSSからデータを得るので Basic認証は不要
    conn = openConnection("http://twitter.com/statuses/user_timeline/" + request[:twitter_id] + ".rss?count=" + count)
    rss = conn.readXML
    request[:link] = rss["/rss/channel[1]/link"].to_s
    rss.collect("/rss/channel[1]/item") { |item| 
      content = item["title"].to_s.gsub(/^.+?: /, "").split(/ RT /, 2)
      quotation = content.length > 1? content[1] : nil
      content = content[0]
      {:time=>item["pubDate"].to_s.parseRFC822Date, 
      :content=>content,
      :quotation=>quotation,
      :link=>item["link"].to_s } 
    }.reverse_each { |item|
      time = item[:time]
      if mintime == nil || mintime > time then
        mintime = time
      end
      if maxtime == nil || maxtime < time then
        maxtime = time
      end
      if year != time.year || month != time.month || day != time.mday then
        year = time.year
        month = time.month
        day = time.mday
        @d = {:year=>year,:month=>month,:day=>day,:tweets=>[]}
        days << @d
      end
      @d[:tweets] << {:hour=>time.hour,:minute=>time.min,:content=>item[:content],
        :quotation=>item[:quotation],:link=>item[:link]}
    }
    days.each {|day|
    	day[:count] = day[:tweets].length;
    	day[:tweets].reject! { |tweet|
    		tweet[:content].match(/^@.+? /)
    	}
    }
    request[:days] = days
    if mintime != nil || maxtime != nil then
      if mintime.year == maxtime.year && mintime.month == maxtime.month && mintime.mday == maxtime.mday then
        request[:title] = mintime.month.to_s + "月" + mintime.mday.to_s + "日のつぶやき"
      else
        request[:title] = mintime.month.to_s + "月" + mintime.mday.to_s + "日〜" + maxtime.month.to_s + "月" + maxtime.mday.to_s + "日のつぶやき"
      end
    end
    numtweets = []
    cnt = 0
    days.slice(1..-1).reverse_each{|day|
    	cnt += day[:count]
    	numtweets << { :year=>day[:year], :month=>day[:month], :day=>day[:day], :cnt=>cnt }
    }
    request[:numtweets] = numtweets
  end
  nil
</ruby:eval>
<html>
<body>
<h1>Twitter -> Mixi</h1>
<form method="post">
Twitter id:<input type="text" name="twitter_id" value="<c:out value="${twitter_id}"/>"><br>
mixi id:<input type="text" name="mixi_id" value="<c:out value="${mixi_id}"/>" size="8"><br>
件数:<input type="text" name="count" value="<c:out value="${count}"/>" size="3"><input type="submit">
<br>
<c:forEach items="${numtweets}" var="n">${n.month}月${n.day}日〜:${n.cnt}件<br></c:forEach>
</form>
<c:if test="${!empty days}">
<h2>Mixi</h2>
<%-- Mixi側に直接ポストするため Mixiのための認証は不要(ブラウザがMixiで認証された状態になっている必要あり) --%>
<form method="post" action="http://mixi.jp/add_diary.pl">
タイトル: <input type="text" name="diary_title" value="<c:out value="${title}"/>" size="32"><br>
内容<br>
<textarea cols="80" rows="20" name="diary_body"><c:forEach items="${days}" var="d" varStatus="status"><c:if test="${!status.first || !status.last}"><strong>${d.month}月${d.day}日</strong>
</c:if><c:forEach items="${d.tweets}" var="t"><a href="${t.link}" target="_blank">${t.hour}時${t.minute}分</a>: <c:out value="${t.content}"/><c:if test="${!empty t.quotation}"><span style="color:#9f009f">(<c:out value="${t.quotation}"/>)</span></c:if>
</c:forEach>
</c:forEach>※この日記は、<a href="<c:out value="${link}"/>" target="_blank">Twitter</a>で発言した内容をまとめたものです。</textarea><br>
<input type="hidden" name="id" value="${param.mixi_id}">
<input type="hidden" name="submit" value="main">
<input type="submit">
</form>
</c:if>
</body>
</html>