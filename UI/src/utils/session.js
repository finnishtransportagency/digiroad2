$(document).ajaxError(function(event, jqXHR, ajaxSettings, thrownError) {
    if (jqXHR.status == 401) {
        window.location = "index.html";
    } else if (jqXHR.status == 403) {
        window.location = "autherror.html";
    }
});

$(document).ajaxComplete(function(event, jqXHR, ajaxSettings) {
    var digiroadResponse = jqXHR.getResponseHeader("Digiroad2-Server-Originated-Response");
    if (!digiroadResponse) {
        window.location = "index.html";
    }
});