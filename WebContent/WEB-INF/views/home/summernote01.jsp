<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>    
<!DOCTYPE html>
<html ng-app="jakdukApp">
  <head>
    <title>Starter Template for Bootstrap</title>
<jsp:include page="../include/html-header.jsp"/>

  <!--summernote dependencies-->
<script src="<%=request.getContextPath()%>/resources/jquery/js/jquery.min.js"></script>
<link href="<%=request.getContextPath()%>/resources/bootstrap/css/bootstrap.css" rel="stylesheet">
<link href="<%=request.getContextPath()%>/resources/summernote/css/summernote.css" rel="stylesheet">
<link href="<%=request.getContextPath()%>/resources/font-awesome/css/font-awesome.min.css" rel="stylesheet">

</head>

<body>
<div class="container">

<jsp:include page="../include/navigation-header.jsp"/>

<div ng-controller="sampleCtrl">
	<div class="starter-template">
		<h1>Bootstrap starter template</h1>
		<p class="lead">Use this document as a way to quickly start any new project.<br> All you get is this text and a mostly barebones HTML document.</p>
	</div>

 <summernote on-image-upload="imageUpload(files, editor)" editable="editable"></summernote>

<input type="file" nv-file-select uploader="uploader"/><br/>
<ul>
    <li ng-repeat="item in uploader.queue">
        Name: <span ng-bind="item.file.name"></span><br/>
        <button ng-click="item.upload()">upload</button>
    </li>
</ul>

<table class="table table-bordered">
<tr><td class="col-xs-3">	Dapibus ac facilisis in</td><td class="col-xs-5">  	<div class="progress">
  <div class="progress-bar progress-bar-success" role="progressbar" aria-valuenow="40" aria-valuemin="0" aria-valuemax="100" style="width: 40%">
    <span class="sr-only">40% Complete (success)</span>
  </div>
</div></td></tr>
</table>

<dl class="dl-horizontal">
  <dt>.dddd.</dt>
  <dd><div class="progress">    
  <div class="progress-bar progress-bar-success" role="progressbar" aria-valuenow="40" aria-valuemin="0" aria-valuemax="100" style="width: 40%">
    <span class="sr-only">40% Complete (success)</span>
  </div>
</div></dd>
</dl>

<div class="row">
  <div class="col-xs-3 col-md-1">
    <a href="#" class="thumbnail">
      <img src="<%=request.getContextPath()%>/image/54bfaad53d96ab5742dfb825" alt="...">
    </a>
  </div>
  sdfsdfsdfsdfsdfsdfsdf
  <div class="progress">    
  <div class="progress-bar progress-bar-success" role="progressbar" aria-valuenow="40" aria-valuemin="0" aria-valuemax="100" style="width: 40%">
    <span class="sr-only">40% Complete (success)</span>
  </div>
</div>
</div>

<div class="row">
  <div class="col-xs-3 col-md-1">
    <a href="#" class="thumbnail">
      <img src="<%=request.getContextPath()%>/image/54bfb5dd3d96ab5742dfb826" alt="...">
    </a>
  </div>
  sdfsdfsdfsdfsdfsdfsdf
  <div class="progress">    
  <div class="progress-bar progress-bar-success" role="progressbar" aria-valuenow="40" aria-valuemin="0" aria-valuemax="100" style="width: 40%">
    <span class="sr-only">40% Complete (success)</span>
  </div>
</div>
</div>

<form action="<c:url value="/image/upload"/>" name="login" method="post" enctype="multipart/form-data">
      <input type="file" name="file">
      <input type="submit">
</form>  
 
</div>	
	
<jsp:include page="../include/footer.jsp"/>
</div><!-- /.container -->

<script src="<%=request.getContextPath()%>/resources/bootstrap/js/bootstrap.min.js"></script>
<script src="<%=request.getContextPath()%>/resources/summernote/js/summernote.min.js"></script>
<script src="<%=request.getContextPath()%>/resources/angular-summernote/js/angular-summernote.min.js"></script>
<script src="<%=request.getContextPath()%>/resources/angular-file-upload/js/angular-file-upload.js"></script>

<script type="text/javascript">
var jakdukApp = angular.module("jakdukApp", ["summernote", "angularFileUpload"]);

jakdukApp.controller("sampleCtrl", function($scope, $http, FileUploader) {
    $scope.options = {
            height: 150,
            toolbar: [
              ['style', ['bold', 'italic', 'underline', 'clear']],
              ['fontsize', ['fontsize']],
              ['color', ['color']],
              ['para', ['ul', 'ol', 'paragraph']],
              ['height', ['height']]
            ]
          };
    
    $scope.uploader = new FileUploader({
    	url:'<c:url value="/image/upload.json"/>',
    	autoUpload:true,
    	method:"POST"
    });
    
	$scope.uploader.filters.push({
    	name: 'imageFilter',
    	fn: function(item /*{File|FileLikeObject}*/, options) {
    	var type = '|' + item.type.slice(item.type.lastIndexOf('/') + 1) + '|';
    	return '|jpg|png|jpeg|bmp|gif|'.indexOf(type) !== -1;
    		}
    	});
	
	$scope.uploader.onAfterAddingFile = function(fileItem) {
		 console.info('onAfterAddingFile', fileItem);
	};	
	
	$scope.uploader.onCompleteItem = function(fileItem, response, status, headers) {
		 console.info('onCompleteItem', response);

		var url = "<%=request.getContextPath()%>/image/" + response.image.id;
		alert(url);
		$scope.editor.insertImage($scope.editable, url);		 
		 
		 };	
    
	$scope.imageUpload = function(files, editor) {
	 console.log('image upload:', files);
	 console.log('image upload:', editor);
	 console.log('image upload\'s editable:', $scope.editable);
	 
		//var url = "<%=request.getContextPath()%>/resources/jakduk/icon/daum_bt.png";       	
		//editor.insertImage($scope.editable, url);
		$scope.editor = editor;
		var bUrl = '<c:url value="/image/upload"/>';
	
		$scope.uploader.addToQueue(files, function() {
			alert("aaa");
		});
      };
    
});
</script>
<jsp:include page="../include/body-footer.jsp"/>
</body>
</html>