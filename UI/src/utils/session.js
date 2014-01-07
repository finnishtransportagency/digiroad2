(function(session, undefined){
    var sessionId = null;
    session.redirectToLogin = function() {
        window.location = "/login.html";
    };

}(window.session = window.session || {}));

$(document).ajaxError(function(event, jqXHR, ajaxSettings, thrownError) {
    if (jqXHR.status == 401) {
        session.redirectToLogin();
    }
});