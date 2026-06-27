package api

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/stevanovicm32/MobilnoRacunarstvo/internal/middleware"
)

func SetupRouter(
	authH *AuthHandler,
	dropH *DropHandler,
	uploadH *UploadHandler,
) *gin.Engine {
	r := gin.Default()

	r.Use(gin.Recovery())
	r.Use(gin.Logger())

	r.Static("/uploads", "./uploads")

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
		api.POST("/uploads", uploadH.UploadImage)
		api.POST("/drops", dropH.CreateDrop)
		api.GET("/drops/heatmap", dropH.GetHeatmap)
		api.GET("/drops/nearby", dropH.GetNearbyDrops)
		api.POST("/drops/:id/claim", dropH.ClaimDrop)
		api.GET("/leaderboard", dropH.GetLeaderboard)
	}

	return r
}
