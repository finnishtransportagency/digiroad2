(function(root) {
  root.UserNotificationCollection = function(backend) {

    var notifications = [];

    this.fetchUnreadNotification = function() {
      backend.userNotificationInfo().then(
        function (result) {
          notifications = result;
            var sortedNotifications = _.sortBy(result, function (notification) {
            return new Date(notification.createdDate);
          });

          if (_.some(result, function (item) { return item.unRead; }))
            eventbus.trigger('userNotification:fetched', sortedNotifications) ;
        });
    };


    this.fetchAll = function() {
      return _.sortBy(notifications, function (notification) {
        return new Date(notification.createdDate);
      });
    };

    this.setNotificationToRead = function(id) {
      var objIndex = _.findIndex( notifications, function(notification) {return notification.id.toString() === id;});
        notifications[objIndex].unRead = false;
    };

  };
})(this);
