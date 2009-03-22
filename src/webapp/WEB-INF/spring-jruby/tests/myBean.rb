p [:hoge, r = myBean.hoge(1,2,3).fetch[0]]
raise "must be 6" unless r == 6

p [:honya, r = myBean.honya]
raise "wrong" unless r == "あいうABC"

true