package api

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/stevanovicm32/MobilnoRacunarstvo/internal/middleware"
)

func SetupRouter(
	authH *AuthHandler,
	dropH *DropHandler,
	collH *CollectionHandler,
) *gin.Engine {
	r := gin.Default()

	r.Use(gin.Recovery())
	r.Use(gin.Logger())

	// Routes
	r.GET("/ping", func(ctx *gin.Context) { ctx.JSON(http.StatusOK, gin.H{"message": "pong"}) })

	auth := r.Group("/auth")
	{
		auth.POST("/register", authH.Register)
		auth.POST("/login", authH.Login)
	}

	api := r.Group("/api")
	api.Use(middleware.AuthMiddleware())
	{
		api.POST("/createDrop", dropH.CreateDrop)
		api.POST("/collect", collH.Collect)
	}

	return r;
}
