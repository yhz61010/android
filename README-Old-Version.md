# AndroidProjectFramework

## Upload to bintray(Maven and jcenter)
```sh
./gradlew clean build bintrayUpload -PbintrayUser=yhz61010 -PbintrayKey=<Your API Key> -PdryRun=false
```
**You can get your API Key as following url [API Key](https://bintray.com/profile/edit)**


Volley Get Request Example:
--------------------
```java
RequestSingleton queue = RequestSingleton.getInstance(context);
Map<String, String> header = new HashMap<>();
header.put("apikey", "1086f416a7ed3ce7ac3c8d3d2cb5fedf");
Map<String, String> param = new HashMap<>();
param.put("citypinyin", "dalian");
GetRequest<WeatherResultBean> get = new GetRequest<>(context, WeatherResultBean.class, "http://apis.baidu.com/apistore/weatherservice/weather", header, param, new Response.Listener<WeatherResultBean>() {
	@Override
	public void onResponse(WeatherResultBean bean) {
		textView.setText(bean.getRetData().getCity() + "\r\n" + bean.getRetData().getDate() + "\r\n" + bean.getRetData().getTime() + "\r\n" + bean.getRetData().getTemp());
	}
}, new Response.ErrorListener() {
	@Override
	public void onErrorResponse(VolleyError volleyError) {
		textView.setText(volleyError.toString());
	}
}, new FinalListener() {
	@Override
	public void onFinal() {
	}
});
queue.addToRequestQueue(get);
```
If you don't want to show progress, just call noProgress() method, like this:
```java
get.noProgress();
```

Volley Post Request Example:
--------------------
```java
RequestSingleton queue = RequestSingleton.getInstance(context);
Map<String, String> postHeader = new HashMap<>();
postHeader.put("apikey", "1086f416a7ed3ce7ac3c8d3d2cb5fedf");
Map<String, String> postParam = new HashMap<>();
postParam.put("page", "1");
postParam.put("limit", "10");
postParam.put("keyword", "大连");
PostRequest<String> postRequest = new PostRequest<>(context, "http://apis.baidu.com/yi18/hospital/search", postHeader, postParam, new Response.Listener<String>() {
	@Override
	public void onResponse(String bean) {
		textView.setText(bean);
	}
}, new Response.ErrorListener() {
	@Override
	public void onErrorResponse(VolleyError volleyError) {
		textView.setText(volleyError.toString());
	}
}, new FinalListener() {
	@Override
	public void onFinal() {
	}
});
queue.addToRequestQueue(postRequest);
```

Volley Json Request Example with JsonObject parameter:
--------------------
```java
JsonArray jsonArrayParam = new JsonArray();
JsonObject obj1 = new JsonObject();
obj1.addProperty("goodsId", 1);
obj1.addProperty("goodsCnt", 1);
JsonObject obj2 = new JsonObject();
obj2.addProperty("goodsId", 2);
obj2.addProperty("goodsCnt", 2);
jsonArrayParam.add(obj1);
jsonArrayParam.add(obj2);
JsonObject objParam = new JsonObject();
objParam.add("goodsList", jsonArrayParam);

RequestSingleton queue = RequestSingleton.getInstance(context);
ObjectJsonRequest<String> jsonsObjRequest = new ObjectJsonRequest<>(context,
		Request.Method.POST, "Your url", objParam, new Response.Listener<String>() {
	@Override
	public void onResponse(String response) {
		textView.setText("Response: " + response);
	}
}, new Response.ErrorListener() {
	@Override
	public void onErrorResponse(VolleyError error) {
		textView.setText("Response Error: " + error);
	}
}, new FinalListener() {
	@Override
	public void onFinal() {
	}
});
queue.addToRequestQueue(jsonsObjRequest);
```

Volley Json Request Example with JsonArray parameter:
--------------------
```java
JsonArray jsonArrayParam = new JsonArray();
JsonObject obj1 = new JsonObject();
obj1.addProperty("goodsId", 1);
obj1.addProperty("goodsCnt", 1);
JsonObject obj2 = new JsonObject();
obj2.addProperty("goodsId", 2);
obj2.addProperty("goodsCnt", 2);
jsonArrayParam.add(obj1);
jsonArrayParam.add(obj2);

RequestSingleton queue = RequestSingleton.getInstance(context);
ArrayJsonRequest<String> arrayJsonRequest = new ObjectJsonRequest<>(context,
		Request.Method.POST, "Your url", jsonArrayParam, new Response.Listener<String>() {
	@Override
	public void onResponse(String response) {
		textView.setText("Response: " + response);
	}
}, new Response.ErrorListener() {
	@Override
	public void onErrorResponse(VolleyError error) {
		textView.setText("Response Error: " + error);
	}
}, new FinalListener() {
	@Override
	public void onFinal() {
	}
});
queue.addToRequestQueue(arrayJsonRequest);
```