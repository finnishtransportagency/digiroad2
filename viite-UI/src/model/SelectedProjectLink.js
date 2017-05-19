(function(root) {
    root.SelectedProjectLink = function() {

        var open = function () {
            eventbus.trigger('ProjectLinkProperties:selected');
        };

        return {
            open: open
        };
    };
})(this);
