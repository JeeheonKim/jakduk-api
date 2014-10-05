<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form"%>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags"%>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html ng-app="jakdukApp">
<head>
<jsp:include page="../include/html-header.jsp"></jsp:include>
</head>
<body>
<sec:authorize access="isAuthenticated()">
	<sec:authentication property="principal.username" var="userName"/>
	<sec:authentication property="Details.bio" var="about"/>
</sec:authorize>
<div class="container">
	<jsp:include page="../include/navigation-header.jsp"/>
	<c:set var="contextPath" value="<%=request.getContextPath()%>"/>
	<div class="container" ng-controller="writeCtrl">
	<form:form commandName="userWrite" action="${contextPath}/oauth/write" method="POST" cssClass="form-horizontal">
		<legend><spring:message code="oauth.register"/> </legend>
		
		<div class="form-group">
			<label class="col-sm-2 control-label" for="username">
				<abbr title='<spring:message code="common.msg.required"/>'>*</abbr> <spring:message code="user.nickname"/>
			</label>
			<div class="col-sm-3">
				<input type="text" class="form-control" size="50" value="${userName}" disabled="disabled">
			</div>
		</div>

		<div class="form-group">
			<label class="col-sm-2 control-label" for="supportFC">
				<spring:message code="user.support.football.club"/>
			</label>
			<div class="col-sm-3">
				<form:select path="footballClub" cssClass="form-control">
					<form:option value="-1"><spring:message code="common.none"/></form:option>
				<c:forEach items="${footballClubs}" var="club">
					<c:forEach items="${club.names}" var="name">
						<form:option value="${club.id}" label="${name.shortName}" class="visible-xs"/>
						<form:option value="${club.id}" label="${name.fullName}" class="visible-sm visible-md visible-lg"/>
					</c:forEach>
				</c:forEach>
				</form:select>
			</div>
		</div>
		<div class="form-group">
			<label class="col-sm-2 control-label" for="about"> <spring:message code="user.comment"/></label>
			<div class="col-sm-4">
				<form:textarea path="about" cssClass="form-control" cols="40" rows="5" placeholder="About"/>
			</div>
		</div>
		<div class="form-group">
			<div class="col-sm-offset-2 col-sm-4">
				<input type="submit" value="<spring:message code="common.button.submit"/>" class="btn btn-default"/>
				<a class="btn btn-danger" href="<c:url value="/"/>"><spring:message code="common.button.cancel"/></a>
			</div> 
		</div>		
	</form:form>
	</div>
</div><!-- /.container -->

<!-- Bootstrap core JavaScript
 ================================================== -->
<!-- Placed at the end of the document so the pages load faster -->
<script src="https://ajax.googleapis.com/ajax/libs/jquery/1.11.0/jquery.min.js"></script>
<script src="<%=request.getContextPath()%>/web-resources/bootstrap/js/bootstrap.min.js"></script>

<script type="text/javascript">
var jakdukApp = angular.module("jakdukApp", []);

jakdukApp.controller("writeCtrl", function($scope) {
});
</script>

<!-- This script should be under the AngularJS which is creating jakdukApp module. -->
<script src="<%=request.getContextPath()%>/web-resources/jakduk/navigation-header.js"></script>   

</body>
</html>