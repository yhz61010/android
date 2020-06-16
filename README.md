## About Log
By default, I used [Xlog](https://github.com/Tencent/mars) as default Log system.  
You can use it easily by calling `CLog.d()` or other handy methods in `CLog`.  
**DO NOT** forget to initialize it first in `Application` by calling `CLog.init(ctx)`.

If you do not want to use [Xlog](https://github.com/Tencent/mars), you can use `LLog` instead.  
This is just a wrapper of android default `Log`. These is no need to initialize it before using it.