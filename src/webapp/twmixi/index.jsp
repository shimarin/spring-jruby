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
	if twitter_id != "" && twitter_id != nil then
		request[:twitter_id] = twitter_id
		mintime = nil
		maxtime = nil 
		year = nil
		month = nil
		day = nil
		days = []
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
		}.reverse.reject{|item|
			item[:content].match(/^@.+? /)
		}.each { |item|
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
		request[:days] = days
		if mintime != nil || maxtime != nil then
			if mintime == maxtime then
				request[:title] = mintime.month.to_s + "月" + mintime.mday.to_s + "日のつぶやき"
			else
				request[:title] = mintime.month.to_s + "月" + mintime.mday.to_s + "日〜" + maxtime.month.to_s + "月" + maxtime.mday.to_s + "日のつぶやき"
			end
		end
	end
	nil
</ruby:eval>
<html>
<body>
<h1>JSP Page</h1>
<h2>Twitter</h2>
<form method="post" action="./">
id:<input type="text" name="twitter_id" value="<c:out value="${twitter_id}"/>"><br>
件数:<input type="text" name="count" value="<c:out value="${count}"/>" size="3"><input type="submit">
</form>
<c:if test="${!empty days}">
<h2>Mixi</h2>
<form method="post" action="http://mixi.jp/add_diary.pl">
id:<input type="text" name="id" value="5168" size="8"><br>
タイトル: <input type="text" name="diary_title" value="<c:out value="${title}"/>" size="32"><br>
内容<br>
<textarea cols="80" rows="20" name="diary_body"><c:forEach items="${days}" var="d"><strong>${d.month}月${d.day}日</strong>
<c:forEach items="${d.tweets}" var="t"><a href="${t.link}" target="_blank">${t.hour}時${t.minute}分</a>: <c:out value="${t.content}"/><c:if test="${!empty t.quotation}"><span style="color:#9f009f">(<c:out value="${t.quotation}"/>)</span></c:if>
</c:forEach>
</c:forEach>※この日記は、<a href="<c:out value="${link}"/>" target="_blank">Twitter</a>で発言した内容をまとめたものです。</textarea><br>
<input type="hidden" name="submit" value="main"><br>
<input type="submit">
</form>
</c:if>
</body>
</html>